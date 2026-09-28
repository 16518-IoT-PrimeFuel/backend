package com.primefuel.fulltank.platform.payment.application.internal.commandservices;

import com.primefuel.fulltank.platform.ordering.api.OrderLookup;
import com.primefuel.fulltank.platform.payment.application.commandservices.PaymentCommandService;
import com.primefuel.fulltank.platform.payment.domain.model.aggregates.Payment;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CompletePaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CreatePaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.RefundPaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.repositories.PaymentRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment command service (S23/T23-B). The essential invariants now live here, in the application layer,
 * instead of in the controller: the order exists and belongs to the declared company, the amount is positive
 * and matches the order snapshot, and there is at most one payment per order.
 *
 * <p>It no longer writes {@code ordering}: {@code complete} records the financial fact and publishes
 * {@code payment.completed.v1}; the ordering <em>compatibility adapter</em> reacts to it and marks its own
 * aggregate paid. So {@code PaymentCompleted} and {@code DeliveryCompleted} are independent facts and the
 * physical/delivery state never depends on payment.
 */
@Service
public class PaymentCommandServiceImpl implements PaymentCommandService {

    private static final String AGGREGATE_TYPE = "Payment";

    private final PaymentRepository paymentRepository;
    private final OrderLookup orderLookup;
    private final EventPublicationRegistry publicationRegistry;

    public PaymentCommandServiceImpl(PaymentRepository paymentRepository,
                                     OrderLookup orderLookup,
                                     EventPublicationRegistry publicationRegistry) {
        this.paymentRepository = paymentRepository;
        this.orderLookup = orderLookup;
        this.publicationRegistry = publicationRegistry;
    }

    @Override
    public Result<Payment, ApplicationError> handle(CreatePaymentCommand command) {
        if (command.orderId() == null || command.companyId() == null) {
            return Result.failure(ApplicationError.validationError("payment",
                    "An order and a buyer company are required"));
        }
        var order = orderLookup.findById(command.orderId())
                .filter(found -> command.companyId().equals(found.companyId()));
        if (order.isEmpty()) {
            return Result.failure(ApplicationError.notFound("FuelOrder", command.orderId().toString()));
        }
        if (command.amount() == null || command.amount() <= 0
                || !command.amount().equals(order.get().totalPrice())) {
            return Result.failure(ApplicationError.validationError("amount",
                    "The payment amount must be positive and match the order total"));
        }
        var existing = paymentRepository.findByOrderId(command.orderId());
        if (existing.isPresent()) {
            return Result.failure(ApplicationError.conflict("Payment",
                    "A payment already exists for order " + command.orderId()));
        }
        return Result.success(paymentRepository.save(new Payment(command)));
    }

    @Override
    @Transactional
    public Result<Payment, ApplicationError> handle(CompletePaymentCommand command) {
        var existing = paymentRepository.findById(command.paymentId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Payment", command.paymentId().toString()));
        }
        var payment = existing.get();
        payment.complete(command.transactionReference());
        var saved = paymentRepository.save(payment);
        // Publish the financial fact; ordering reacts via its compatibility adapter (no cross-domain write).
        publicationRegistry.publish("payment.completed.v1", AGGREGATE_TYPE, String.valueOf(saved.getId()),
                saved.getCompanyId(), 1L,
                "{\"paymentId\":" + saved.getId()
                        + ",\"orderId\":" + saved.getOrderId()
                        + ",\"amount\":" + saved.getAmount() + "}");
        return Result.success(saved);
    }

    @Override
    @Transactional
    public Result<Payment, ApplicationError> handle(RefundPaymentCommand command) {
        var existing = paymentRepository.findById(command.paymentId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Payment", command.paymentId().toString()));
        }
        var payment = existing.get();
        payment.refund();
        return Result.success(paymentRepository.save(payment));
    }
}
