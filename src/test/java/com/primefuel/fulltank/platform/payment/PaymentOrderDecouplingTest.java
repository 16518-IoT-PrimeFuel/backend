package com.primefuel.fulltank.platform.payment;

import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.payment.application.commandservices.PaymentCommandService;
import com.primefuel.fulltank.platform.payment.domain.model.aggregates.Payment;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CompletePaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CreatePaymentCommand;
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
                "Av. Decoupling 1", LocalDate.parse("2026-10-15")), 50.0);
        return fuelOrderRepository.save(order).getId();
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
        assertThat(fuelOrderRepository.findById(orderId).orElseThrow().getStatus().name()).isEqualTo("PENDING");
        assertThat(publicationRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc(
                "Payment", String.valueOf(created.getOrElse(null).getId()))).isEmpty();
    }
}
