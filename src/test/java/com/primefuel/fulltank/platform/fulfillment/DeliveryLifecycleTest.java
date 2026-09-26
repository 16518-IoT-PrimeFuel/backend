package com.primefuel.fulltank.platform.fulfillment;

import com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices.DeliveryLifecycleServiceImpl;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.ArriveDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.AssignDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CancelDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CompletePhysicalDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.FailDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.StartDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.entities.DeliveryStateTransition;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryStatus;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryStateTransitionRepository;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.EventPublicationPersistenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * T14-A: the physical machine advanced through the command service — journal, outbox events, optimistic
 * version, evidence volume and the legacy compatibility map.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:delivery_lifecycle;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class DeliveryLifecycleTest {

    private static final AtomicLong ORDERS = new AtomicLong(7000);

    @Autowired
    private DeliveryLifecycleServiceImpl lifecycle;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryStateTransitionRepository transitionRepository;

    @Autowired
    private EventPublicationPersistenceRepository publications;

    @MockitoBean
    private FuelOrderQueryService fuelOrderQueryService;

    /** Seeds a delivery the way the legacy create flow leaves it: already dispatched, no physical state. */
    private Delivery seedDelivery(Long providerId, double requestedVolume) {
        var orderId = ORDERS.incrementAndGet();
        when(fuelOrderQueryService.findRequestedQuantity(anyLong())).thenReturn(Optional.of(requestedVolume));
        var delivery = new Delivery(new CreateDeliveryCommand(orderId, providerId, 11L, 22L, "2026-10-01", "seed"));
        delivery.dispatch();
        return deliveryRepository.save(delivery);
    }

    private long eventsFor(Long deliveryId) {
        return publications.findByAggregateTypeAndAggregateIdOrderByIdAsc("Delivery", String.valueOf(deliveryId))
                .size();
    }

    @Test
    void walksTheWholeMachineJournallingEveryStepWithTheObservedVersionPerRow() {
        var delivery = seedDelivery(1L, 100.0);
        var id = delivery.getId();

        var assigned = lifecycle.handle(new AssignDeliveryCommand(id));
        var started = lifecycle.handle(new StartDeliveryCommand(id));
        var arrived = lifecycle.handle(new ArriveDeliveryCommand(id));
        var completed = lifecycle.handle(new CompletePhysicalDeliveryCommand(id, 80.0));

        assertThat(assigned.isSuccess()).isTrue();
        assertThat(started.isSuccess()).isTrue();
        assertThat(arrived.isSuccess()).isTrue();
        assertThat(completed.isSuccess()).isTrue();
        var assignedVersion = assigned.getOrElse(null).getVersion();
        var startedVersion = started.getOrElse(null).getVersion();
        var arrivedVersion = arrived.getOrElse(null).getVersion();

        var finalState = completed.getOrElse(null);
        assertThat(finalState.currentPhysicalState()).isEqualTo(DeliveryPhysicalState.COMPLETED);
        assertThat(finalState.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(finalState.getDeliveredVolume()).isEqualTo(80.0);
        assertThat(finalState.getRequestedVolume()).isEqualTo(100.0);

        // A partial delivery (80 < 100) is legal and both values are kept.
        var journal = transitionRepository.findByDeliveryId(id);
        assertThat(journal)
                .extracting(DeliveryStateTransition::toState)
                .containsExactly(DeliveryPhysicalState.ASSIGNED, DeliveryPhysicalState.STARTED,
                        DeliveryPhysicalState.ARRIVED, DeliveryPhysicalState.DELIVERING,
                        DeliveryPhysicalState.COMPLETED);
        // Row by row, each entry carries the version the aggregate really held at *that* transition — the
        // discharge close is not stamped with the post-completion version.
        assertThat(journal)
                .extracting(DeliveryStateTransition::aggregateVersion)
                .containsExactly(
                        (long) assignedVersion,
                        (long) startedVersion,
                        (long) arrivedVersion,
                        (long) arrivedVersion + 1,
                        (long) finalState.getVersion());
        // Closing from ARRIVED consumes two versions: first DELIVERING, then COMPLETED.
        assertThat(finalState.getVersion()).isEqualTo(arrivedVersion + 2);
        assertThat(eventsFor(id)).isEqualTo(4);
    }

    @Test
    void anIllegalTransitionIsRejectedAndLeavesNothingBehind() {
        var delivery = seedDelivery(2L, 100.0);
        var id = delivery.getId();

        // An assigned delivery cannot arrive without starting first.
        var result = lifecycle.handle(new ArriveDeliveryCommand(id));

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getOrElse(null)).isNull();
        assertThat(transitionRepository.findByDeliveryId(id)).isEmpty();
        assertThat(eventsFor(id)).isZero();
        assertThat(deliveryRepository.findById(id).orElseThrow().getPhysicalState()).isNull();
    }

    @Test
    void completingTwiceDoesNotDoubleCountTheVolume() {
        var delivery = seedDelivery(3L, 100.0);
        var id = delivery.getId();
        lifecycle.handle(new StartDeliveryCommand(id));
        lifecycle.handle(new ArriveDeliveryCommand(id));
        assertThat(lifecycle.handle(new CompletePhysicalDeliveryCommand(id, 50.0)).isSuccess()).isTrue();

        var second = lifecycle.handle(new CompletePhysicalDeliveryCommand(id, 30.0));

        assertThat(second.isFailure()).isTrue();
        assertThat(deliveryRepository.findById(id).orElseThrow().getDeliveredVolume()).isEqualTo(50.0);
        assertThat(eventsFor(id)).isEqualTo(3);
    }

    @Test
    void rejectsADeliveredVolumeAboveTheRequestedOne() {
        var delivery = seedDelivery(4L, 100.0);
        var id = delivery.getId();
        lifecycle.handle(new StartDeliveryCommand(id));
        lifecycle.handle(new ArriveDeliveryCommand(id));

        var result = lifecycle.handle(new CompletePhysicalDeliveryCommand(id, 150.0));

        assertThat(result.isFailure()).isTrue();
        assertThat(deliveryRepository.findById(id).orElseThrow().currentPhysicalState())
                .isEqualTo(DeliveryPhysicalState.ARRIVED);
    }

    @Test
    void rejectsAMissingDeliveredVolume() {
        var delivery = seedDelivery(5L, 100.0);
        var id = delivery.getId();
        lifecycle.handle(new StartDeliveryCommand(id));
        lifecycle.handle(new ArriveDeliveryCommand(id));

        assertThat(lifecycle.handle(new CompletePhysicalDeliveryCommand(id, null)).isFailure()).isTrue();
    }

    @Test
    void rejectsACompleteWhenTheRequestedVolumeCannotBeResolved() {
        var delivery = seedDelivery(10L, 100.0);
        var id = delivery.getId();
        lifecycle.handle(new StartDeliveryCommand(id));
        lifecycle.handle(new ArriveDeliveryCommand(id));
        // A legacy/malformed order with no resolvable requested quantity: the close must not persist an
        // ambiguous requestedVolume=null beside a real delivered one (U11).
        when(fuelOrderQueryService.findRequestedQuantity(delivery.getOrderId())).thenReturn(Optional.empty());

        var result = lifecycle.handle(new CompletePhysicalDeliveryCommand(id, 42.0));

        assertThat(result.isFailure()).isTrue();
        var stored = deliveryRepository.findById(id).orElseThrow();
        assertThat(stored.currentPhysicalState()).isEqualTo(DeliveryPhysicalState.ARRIVED);
        assertThat(stored.getRequestedVolume()).isNull();
        assertThat(stored.getDeliveredVolume()).isNull();
        // Only start and arrive were journalled; the rejected close wrote nothing.
        assertThat(transitionRepository.findByDeliveryId(id)).hasSize(2);
        assertThat(eventsFor(id)).isEqualTo(2);
    }

    @Test
    void aDeliveryCanFailFromAMidState() {
        var delivery = seedDelivery(6L, 100.0);
        var id = delivery.getId();
        lifecycle.handle(new StartDeliveryCommand(id));

        var failed = lifecycle.handle(new FailDeliveryCommand(id, "site closed"));

        assertThat(failed.isSuccess()).isTrue();
        assertThat(failed.getOrElse(null).currentPhysicalState()).isEqualTo(DeliveryPhysicalState.FAILED);
        assertThat(failed.getOrElse(null).getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(failed.getOrElse(null).getNotes()).isEqualTo("site closed");
    }

    @Test
    void aDeliveryCanBeCancelledExplicitly() {
        var delivery = seedDelivery(7L, 100.0);
        var id = delivery.getId();

        var cancelled = lifecycle.handle(new CancelDeliveryCommand(id, "buyer withdrew"));

        assertThat(cancelled.isSuccess()).isTrue();
        assertThat(cancelled.getOrElse(null).currentPhysicalState()).isEqualTo(DeliveryPhysicalState.CANCELLED);
        assertThat(cancelled.getOrElse(null).getPhysicalState()).isEqualTo(DeliveryPhysicalState.CANCELLED);
        // Legacy has no CANCELLED: it is mapped to FAILED for v1 readers.
        assertThat(cancelled.getOrElse(null).getStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void assigningIsIdempotentAndDoesNotDuplicateTheEvent() {
        var delivery = seedDelivery(8L, 100.0);
        var id = delivery.getId();

        assertThat(lifecycle.handle(new AssignDeliveryCommand(id)).isSuccess()).isTrue();
        assertThat(lifecycle.handle(new AssignDeliveryCommand(id)).isSuccess()).isTrue();

        assertThat(eventsFor(id)).isEqualTo(1);
        assertThat(transitionRepository.findByDeliveryId(id)).hasSize(1);
    }

    @Test
    void aLegacyDeliveryIsReadAsAssignedWithoutBeingRewritten() {
        var delivery = seedDelivery(9L, 100.0);
        var id = delivery.getId();

        var stored = deliveryRepository.findById(id).orElseThrow();

        assertThat(stored.currentPhysicalState()).isEqualTo(DeliveryPhysicalState.ASSIGNED);
        assertThat(stored.getPhysicalState()).isNull();
    }
}
