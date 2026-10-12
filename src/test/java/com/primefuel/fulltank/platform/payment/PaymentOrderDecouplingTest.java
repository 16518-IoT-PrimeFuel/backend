package com.primefuel.fulltank.platform.payment;

import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.payment.application.commandservices.PaymentCommandService;
import com.primefuel.fulltank.platform.payment.domain.model.aggregates.Payment;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CompletePaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CreatePaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.FailPaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.RefundPaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentStatus;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentMethod;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.EventPublicationPersistenceRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T23-B: the order is marked paid through the {@code payment.completed.v1} event (ordering's own compatibility
 * adapter), not by payment writing ordering; and the payment/order facts are independent of the physical
 * delivery lifecycle.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:payment_decoupling;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class PaymentOrderDecouplingTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private PaymentCommandService paymentCommandService;

    @Autowired
    private FuelOrderRepository fuelOrderRepository;

    @Autowired
    private EventPublicationPersistenceRepository publicationRepository;

    private long order(long companyId, long providerId) {
        var order = new FuelOrder(new CreateFuelOrderCommand(companyId, providerId, 1L, null, 50.0,
                "Av. Decoupling 1", LocalDate.parse("2099-10-15"), null), 50.0);
        order.setStatus(com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.OrderStatus.PENDING_PAYMENT);
        return fuelOrderRepository.save(order).getId();
    }

    private static String code(Result<Payment, ApplicationError> result) {
        return ((Result.Failure<Payment, ApplicationError>) result).error().code();
    }

    private long pendingPayment(long companyId, String operationNumber) {
        return paymentCommandService
                .handle(new CreatePaymentCommand(order(companyId, 1L), companyId, 1.0, PaymentMethod.CASH,
                        operationNumber))
                .getOrElse(null).getId();
    }

    @Test
    void chargesTheOrderTotalIgnoringTheClientAmount() {
        long companyId = 800L + SEQUENCE.incrementAndGet();
        long orderId = order(companyId, 1L);
        var payment = paymentCommandService
                .handle(new CreatePaymentCommand(orderId, companyId, 0.01, PaymentMethod.CASH))
                .getOrElse(null);
        assertThat(payment.getAmount()).isEqualTo(50.0);
        assertThat(payment.getCurrency()).isEqualTo("PEN");
    }

    @Test
    void secondPaymentOnTheSameOrderOrOnAnOrderNotAwaitingPaymentIsAConflict() {
        long companyId = 810L + SEQUENCE.incrementAndGet();
        long orderId = order(companyId, 1L);
        var first = new CreatePaymentCommand(orderId, companyId, 50.0, PaymentMethod.CASH);
        assertThat(paymentCommandService.handle(first).isSuccess()).isTrue();
        assertThat(code(paymentCommandService.handle(first))).isEqualTo("PAYMENT_CONFLICT");

        var pending = fuelOrderRepository.save(new FuelOrder(new CreateFuelOrderCommand(companyId, 1L, 1L, null,
                50.0, "Av. Decoupling 1", LocalDate.parse("2099-10-15"), null), 50.0));
        assertThat(code(paymentCommandService.handle(new CreatePaymentCommand(pending.getId(), companyId, 50.0,
                PaymentMethod.CASH)))).isEqualTo("PAYMENT_CONFLICT");
    }

    @Test
    void operationNumberIsValidatedAndUnique() {
        long companyId = 820L + SEQUENCE.incrementAndGet();
        String number = "OP" + System.nanoTime();
        pendingPayment(companyId, number);
        assertThat(code(paymentCommandService.handle(new CreatePaymentCommand(order(companyId, 1L), companyId, 50.0,
                PaymentMethod.CASH, number)))).isEqualTo("PAYMENT_CONFLICT");
        assertThat(code(paymentCommandService.handle(new CreatePaymentCommand(order(companyId, 1L), companyId, 50.0,
                PaymentMethod.CASH, "ab-1")))).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void completingTwiceIsRejectedAndPublishesTheEventOnce() {
        long companyId = 830L + SEQUENCE.incrementAndGet();
        long paymentId = pendingPayment(companyId, null);
        assertThat(paymentCommandService.handle(new CompletePaymentCommand(paymentId, "r1")).isSuccess()).isTrue();
        assertThat(paymentCommandService.handle(new CompletePaymentCommand(paymentId, "r2")).isSuccess()).isFalse();
        assertThat(publicationRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc("Payment",
                String.valueOf(paymentId))).extracting("eventType").containsOnlyOnce("payment.completed.v1");
    }

    @Test
    void aFailedPaymentFreesTheOrderForARetry() {
        long companyId = 840L + SEQUENCE.incrementAndGet();
        long orderId = order(companyId, 1L);
        long failedId = paymentCommandService
                .handle(new CreatePaymentCommand(orderId, companyId, 50.0, PaymentMethod.CASH))
                .getOrElse(null).getId();
        assertThat(paymentCommandService.handle(new FailPaymentCommand(failedId)).getOrElse(null).getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(paymentCommandService.handle(new FailPaymentCommand(failedId)).isSuccess()).isFalse();

        var retry = paymentCommandService
                .handle(new CreatePaymentCommand(orderId, companyId, 50.0, PaymentMethod.CASH));
        assertThat(retry.isSuccess()).isTrue();
        assertThat(retry.getOrElse(null).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void onlyCompletedPaymentsCanBeRefundedAndRepeatingIsIdempotent() {
        long companyId = 950L + SEQUENCE.incrementAndGet();
        long orderId = order(companyId, 1L);
        long paymentId = paymentCommandService
                .handle(new CreatePaymentCommand(orderId, companyId, 50.0, PaymentMethod.CASH))
                .getOrElse(null).getId();

        assertThat(paymentCommandService.handle(new RefundPaymentCommand(paymentId)).isSuccess()).isFalse();

        paymentCommandService.handle(new CompletePaymentCommand(paymentId, "ref-refund"));
        var refunded = paymentCommandService.handle(new RefundPaymentCommand(paymentId));
        assertThat(refunded.getOrElse(null).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(paymentCommandService.handle(new RefundPaymentCommand(paymentId)).isSuccess()).isTrue();
    }

    @Test
    void completingAPaymentMarksTheOrderPaidThroughThePublishedEvent() {
        long companyId = 900L + SEQUENCE.incrementAndGet();
        long orderId = order(companyId, 1L);
        long paymentId = paymentCommandService
                .handle(new CreatePaymentCommand(orderId, companyId, 50.0, PaymentMethod.CASH))
                .getOrElse(null).getId();

        assertThat(paymentCommandService.handle(new CompletePaymentCommand(paymentId, "ref-1")).isSuccess())
                .isTrue();

        // The order is paid — but payment never wrote ordering directly; it published payment.completed.v1 and
        // ordering's compatibility adapter reacted.
        assertThat(fuelOrderRepository.findById(orderId).orElseThrow().getStatus().name()).isEqualTo("PAID");
        var publications = publicationRepository
                .findByAggregateTypeAndAggregateIdOrderByIdAsc("Payment", String.valueOf(paymentId));
        assertThat(publications).extracting("eventType").contains("payment.completed.v1");
    }

    @Test
    void paymentAndOrderSnapshotsStayIndependentOfDelivery() {
        long companyId = 901L + SEQUENCE.incrementAndGet();
        long orderId = order(companyId, 1L);
        Result<Payment, ApplicationError> created = paymentCommandService
                .handle(new CreatePaymentCommand(orderId, companyId, 50.0, PaymentMethod.CASH));

        // Creating a payment leaves the order untouched (PENDING) and does not publish anything yet.
        assertThat(created.isSuccess()).isTrue();
        assertThat(fuelOrderRepository.findById(orderId).orElseThrow().getStatus().name()).isEqualTo("PENDING_PAYMENT");
        assertThat(publicationRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc(
                "Payment", String.valueOf(created.getOrElse(null).getId()))).isEmpty();
    }
}
