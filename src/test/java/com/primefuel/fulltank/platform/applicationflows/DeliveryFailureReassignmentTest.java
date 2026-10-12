package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices.DeliveryLifecycleServiceImpl;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CancelDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.FailDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.iam.application.commandservices.OnboardingCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.commands.OnboardOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationType;
import com.primefuel.fulltank.platform.notification.domain.repositories.NotificationRepository;
import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.OrderStatus;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AttachReplenishmentOrderCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.EventPublicationPersistenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A failed/cancelled delivery must not orphan its order: the order returns to CONFIRMED, the live-delivery
 * uniqueness is released so it can be reassigned, and the delivery events reach only the organizations that
 * own the delivery (two buyers, one provider: each buyer is notified only about its own order).
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:delivery_failure_reassign;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class DeliveryFailureReassignmentTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T09:00:00Z");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private AssignDeliveryFlow assignDeliveryFlow;
    @Autowired
    private DeliveryLifecycleServiceImpl lifecycle;
    @Autowired
    private FleetRegistry fleetRegistry;
    @Autowired
    private FuelProductCommandService fuelProductCommandService;
    @Autowired
    private ReplenishmentCommandService replenishmentCommandService;
    @Autowired
    private OnboardingCommandService onboardingCommandService;
    @Autowired
    private FuelOrderRepository orderRepository;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private EventPublicationPersistenceRepository publicationRepository;

    /** A buyer organization (its owner is the only member) with an accepted request attached to a real order. */
    private record Buyer(long organizationId, long ownerUserId, long orderId) {
    }

    private Buyer buyer(long providerId, long productId, long ownerUserId) {
        var organization = onboardingCommandService.handle(new OnboardOrganizationCommand(
                "Failure Org " + SEQUENCE.incrementAndGet(), "FLR" + SEQUENCE.incrementAndGet(),
                OrganizationType.CUSTOMER, ownerUserId));
        assertThat(organization.isSuccess()).isTrue();
        long organizationId = organization.getOrElse(null).getId();
        long orderId = orderRepository.save(new FuelOrder(new CreateFuelOrderCommand(organizationId, providerId,
                productId, null, 100.0, "Av. Prueba", LocalDate.parse("2099-10-15"), null), 1000.0)).getId();
        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                organizationId, null, 1L, providerId, productId, 100.0, "LITRE", ReplenishmentSource.MANUAL,
                "failure-" + SEQUENCE.incrementAndGet(), "Av. Prueba", null));
        assertThat(created.isSuccess()).isTrue();
        long requestId = created.getOrElse(null).getId();
        assertThat(replenishmentCommandService.handle(new AcceptReplenishmentRequestCommand(requestId)).isSuccess())
                .isTrue();
        assertThat(replenishmentCommandService.handle(new AttachReplenishmentOrderCommand(requestId, orderId))
                .isSuccess()).isTrue();
        return new Buyer(organizationId, ownerUserId, orderId);
    }

    private long product(long providerId) {
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel failure " + SEQUENCE.incrementAndGet(), FuelType.DIESEL, 10.0, "LITRE", 5000.0, 5000.0,
                providerId, true));
        assertThat(product.isSuccess()).isTrue();
        return product.getOrElse(null).getId();
    }

    private long driver(long providerId) {
        return fleetRegistry.registerDriver(new RegisterDriverCommand(providerId, null, "Fail", "Driver",
                "L-FAIL-" + SEQUENCE.incrementAndGet(), "999000444", "fail-driver@example.test", "AVAILABLE"))
                .getOrElse(null).id();
    }

    private long tanker(long providerId) {
        return fleetRegistry.registerTanker(new RegisterTankerCommand(providerId,
                "FL-" + SEQUENCE.incrementAndGet(), "Volvo", "FH", 1000.0, "LITRE", "AVAILABLE"))
                .getOrElse(null).id();
    }

    private AssignDeliveryResult assign(Buyer buyer, long providerId, long driverId, long tankerId, String commandId) {
        var result = assignDeliveryFlow.assign(new AssignDeliveryFlowCommand(commandId, buyer.orderId(), providerId,
                driverId, tankerId, T0, T1, "2026-10-01", "failure test"));
        assertThat(result.isSuccess()).as("assignment %s", commandId).isTrue();
        return result.getOrElse(null);
    }

    private long failedNotifications(long userId) {
        return notificationRepository.findByUserId(userId).stream()
                .filter(notification -> notification.getType() == NotificationType.DELIVERY_FAILED).count();
    }

    @Test
    void failedDeliveryReopensTheOrderAndAllowsReassignment() {
        long providerId = 70_000L + SEQUENCE.incrementAndGet();
        long productId = product(providerId);
        long driverId = driver(providerId);
        long tankerId = tanker(providerId);
        var buyer = buyer(providerId, productId, 7001);

        var first = assign(buyer, providerId, driverId, tankerId, "cmd-fail-1-" + SEQUENCE.incrementAndGet());
        assertThat(orderRepository.findById(buyer.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DISPATCHED);

        assertThat(lifecycle.handle(new FailDeliveryCommand(first.deliveryId(), "road closed")).isSuccess()).isTrue();

        assertThat(orderRepository.findById(buyer.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        assertThat(deliveryRepository.findByOrderId(buyer.orderId())).isEmpty();

        var second = assign(buyer, providerId, driverId, tankerId, "cmd-fail-2-" + SEQUENCE.incrementAndGet());
        assertThat(second.deliveryId()).isNotEqualTo(first.deliveryId());
        assertThat(deliveryRepository.findByOrderId(buyer.orderId()).orElseThrow().getId())
                .isEqualTo(second.deliveryId());
        assertThat(deliveryRepository.findById(first.deliveryId()).orElseThrow().currentPhysicalState())
                .isEqualTo(DeliveryPhysicalState.FAILED);
        assertThat(orderRepository.findById(buyer.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DISPATCHED);
    }

    @Test
    void cancelledDeliveryAlsoAllowsReassignment() {
        long providerId = 70_000L + SEQUENCE.incrementAndGet();
        long productId = product(providerId);
        long driverId = driver(providerId);
        long tankerId = tanker(providerId);
        var buyer = buyer(providerId, productId, 7002);

        var first = assign(buyer, providerId, driverId, tankerId, "cmd-cancel-1-" + SEQUENCE.incrementAndGet());
        assertThat(lifecycle.handle(new CancelDeliveryCommand(first.deliveryId(), "driver unavailable")).isSuccess())
                .isTrue();

        assertThat(orderRepository.findById(buyer.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        assertThat(assign(buyer, providerId, driverId, tankerId, "cmd-cancel-2-" + SEQUENCE.incrementAndGet())
                .deliveryId()).isNotEqualTo(first.deliveryId());
    }

    @Test
    void deliveryEventsAreScopedToTheBuyerOwningTheOrder() {
        long providerId = 70_000L + SEQUENCE.incrementAndGet();
        long productId = product(providerId);
        var buyerA = buyer(providerId, productId, 7003);
        var buyerB = buyer(providerId, productId, 7004);
        var deliveryA = assign(buyerA, providerId, driver(providerId), tanker(providerId),
                "cmd-iso-a-" + SEQUENCE.incrementAndGet());
        assign(buyerB, providerId, driver(providerId), tanker(providerId), "cmd-iso-b-" + SEQUENCE.incrementAndGet());

        assertThat(lifecycle.handle(new FailDeliveryCommand(deliveryA.deliveryId(), "flat tyre")).isSuccess())
                .isTrue();

        assertThat(failedNotifications(buyerA.ownerUserId())).isEqualTo(1);
        assertThat(failedNotifications(buyerB.ownerUserId())).isZero();
        var events = publicationRepository.findByAggregateTypeAndAggregateIdOrderByIdAsc(
                "Delivery", String.valueOf(deliveryA.deliveryId()));
        assertThat(events).filteredOn(event -> "delivery.failed.v1".equals(event.getEventType()))
                .extracting("organizationId")
                .contains(buyerA.organizationId())
                .doesNotContain(buyerB.organizationId());
    }
}
