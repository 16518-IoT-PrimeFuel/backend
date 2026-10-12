package com.primefuel.fulltank.platform.payment.application.internal.commandservices;

import com.primefuel.fulltank.platform.ordering.api.OrderLookup;
import com.primefuel.fulltank.platform.payment.application.commandservices.PaymentCommandService;
import com.primefuel.fulltank.platform.payment.domain.model.aggregates.Payment;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CompletePaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CreatePaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.FailPaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.RefundPaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentStatus;
import com.primefuel.fulltank.platform.payment.domain.repositories.PaymentRepository;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment command service (S23/T23-B). The essential invariants live here, in the application layer: the order
 * exists, belongs to the declared company and is awaiting payment (PENDING_PAYMENT); the charged amount and the
 * company are taken from the order, never from the client; there is at most one non-FAILED payment per order
 * (also enforced by a DB constraint); the operation number is well-formed and unique.
 *
 * <p>It no longer writes {@code ordering}: {@code complete} records the financial fact and publishes
 * {@code payment.completed.v1}; the ordering <em>compatibility adapter</em> reacts to it and marks its own
 * aggregate paid. So {@code PaymentCompleted} and {@code DeliveryCompleted} are independent facts and the
 * physical/delivery state never depends on payment.
 */
@Service
@Transactional
public class PaymentCommandServiceImpl implements PaymentCommandService {

    private static final String AGGREGATE_TYPE = "Payment";
    private static final Pattern OPERATION_NUMBER = Pattern.compile("[A-Za-z0-9]{6,20}");

    private final PaymentRepository paymentRepository;
    private final OrderLookup orderLookup;
    private final ReplenishmentLookup replenishmentLookup;
    private final EventPublicationRegistry publicationRegistry;

    public PaymentCommandServiceImpl(PaymentRepository paymentRepository,
                                     OrderLookup orderLookup,
                                     ReplenishmentLookup replenishmentLookup,
                                     EventPublicationRegistry publicationRegistry) {
        this.paymentRepository = paymentRepository;
        this.orderLookup = orderLookup;
        this.replenishmentLookup = replenishmentLookup;
        this.publicationRegistry = publicationRegistry;
    }

    @Override
    public Result<Payment, ApplicationError> handle(CreatePaymentCommand command) {
        if (command.orderId() == null || command.companyId() == null) {
            return Result.failure(ApplicationError.validationError("payment",
                    "An order and a buyer company are required"));
        }
        var found = orderLookup.findById(command.orderId())
                .filter(o -> command.companyId().equals(o.companyId()));
        if (found.isEmpty()) {
            return Result.failure(ApplicationError.notFound("FuelOrder", command.orderId().toString()));
        }
        var order = found.get();
        var operationNumber = command.operationNumber() == null || command.operationNumber().isBlank()
                ? null : command.operationNumber().trim();
        if (operationNumber != null && !OPERATION_NUMBER.matcher(operationNumber).matches()) {
            return Result.failure(ApplicationError.validationError("operationNumber",
                    "The operation number must be 6 to 20 alphanumeric characters"));
        }
        if (order.totalPrice() == null || order.totalPrice() <= 0) {
            return Result.failure(ApplicationError.validationError("amount",
                    "The order has no positive total to charge"));
        }
        if (!"PENDING_PAYMENT".equals(order.status())) {
            return Result.failure(ApplicationError.conflict("Payment",
                    "Order " + order.id() + " is not awaiting payment; current status is " + order.status()));
        }
        if (paymentRepository.findByOrderId(order.id())
                .filter(p -> p.getStatus() != PaymentStatus.FAILED).isPresent()) {
            return Result.failure(ApplicationError.conflict("Payment",
                    "A payment already exists for order " + order.id()));
        }
        if (operationNumber != null && paymentRepository.existsByOperationNumber(operationNumber)) {
            return Result.failure(ApplicationError.conflict("Payment",
                    "Operation number " + operationNumber + " is already registered"));
        }
        // Amount and company come from the order snapshot; the client values were only used to authorize.
        // ponytail: a concurrent duplicate is caught by uk_payments_* (500); map it to 409 if it shows up.
        return Result.success(paymentRepository.save(new Payment(new CreatePaymentCommand(
                order.id(), order.companyId(), order.totalPrice(), command.paymentMethod(), operationNumber))));
    }

    @Override
    public Result<Payment, ApplicationError> handle(CompletePaymentCommand command) {
        var existing = paymentRepository.findById(command.paymentId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Payment", command.paymentId().toString()));
        }
        var payment = existing.get();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return Result.failure(ApplicationError.conflict("Payment",
                    "Only PENDING payments can be completed; current status is " + payment.getStatus()));
        }
        payment.complete(command.transactionReference());
        var saved = paymentRepository.save(payment);
        // Publish the financial fact; ordering reacts via its compatibility adapter (no cross-domain write).
        publicationRegistry.publish("payment.completed.v1", AGGREGATE_TYPE, String.valueOf(saved.getId()),
                organizationOf(saved), 1L,
                "{\"paymentId\":" + saved.getId()
                        + ",\"orderId\":" + saved.getOrderId()
                        + ",\"amount\":" + saved.getAmount() + "}");
        return Result.success(saved);
    }

    /** A rejected/errored charge: PENDING -> FAILED frees the order for a new payment attempt. */
    @Override
    public Result<Payment, ApplicationError> handle(FailPaymentCommand command) {
        var existing = paymentRepository.findById(command.paymentId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Payment", command.paymentId().toString()));
        }
        var payment = existing.get();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return Result.failure(ApplicationError.conflict("Payment",
                    "Only PENDING payments can be failed; current status is " + payment.getStatus()));
        }
        payment.fail();
        var saved = paymentRepository.save(payment);
        publicationRegistry.publish("payment.failed.v1", AGGREGATE_TYPE, String.valueOf(saved.getId()),
                organizationOf(saved), 1L,
                "{\"paymentId\":" + saved.getId()
                        + ",\"orderId\":" + saved.getOrderId()
                        + ",\"amount\":" + saved.getAmount() + "}");
        return Result.success(saved);
    }

    /**
     * The event scope is the buyer's organization (the request behind the order), not the legacy company id,
     * which is a different id space. ponytail: unresolvable (legacy order without request) falls back to the
     * company id rather than dropping the event, which ordering needs to mark the order paid.
     */
    private Long organizationOf(Payment payment) {
        return replenishmentLookup.findByOrderId(payment.getOrderId())
                .map(ReplenishmentLookup.ReplenishmentView::organizationId)
                .orElse(payment.getCompanyId());
    }

    @Override
    public Result<Payment, ApplicationError> handle(RefundPaymentCommand command) {
        var existing = paymentRepository.findById(command.paymentId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Payment", command.paymentId().toString()));
        }
        var payment = existing.get();
        // Solo un cobro completado se reembolsa; repetir sobre REFUNDED es idempotente.
        // ponytail: solo cambia el estado; la devolución real llega con la pasarela de pagos.
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return Result.success(payment);
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            return Result.failure(ApplicationError.conflict("Payment",
                    "Only COMPLETED payments can be refunded; current status is " + payment.getStatus()));
        }
        payment.refund();
        return Result.success(paymentRepository.save(payment));
    }
}
