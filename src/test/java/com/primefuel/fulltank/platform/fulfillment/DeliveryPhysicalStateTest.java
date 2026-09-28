package com.primefuel.fulltank.platform.fulfillment;

import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryStatus;
import org.junit.jupiter.api.Test;

import static com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState.ARRIVED;
import static com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState.ASSIGNED;
import static com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState.CANCELLED;
import static com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState.COMPLETED;
import static com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState.DELIVERING;
import static com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState.FAILED;
import static com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

/** T14-A: the transition table and the legacy compatibility map, without any framework. */
class DeliveryPhysicalStateTest {

    @Test
    void allowsTheHappyPath() {
        assertThat(ASSIGNED.canTransitionTo(STARTED)).isTrue();
        assertThat(STARTED.canTransitionTo(ARRIVED)).isTrue();
        assertThat(ARRIVED.canTransitionTo(DELIVERING)).isTrue();
        assertThat(DELIVERING.canTransitionTo(COMPLETED)).isTrue();
    }

    @Test
    void rejectsSkippingAheadAndGoingBackwards() {
        assertThat(ASSIGNED.canTransitionTo(ARRIVED)).isFalse();
        assertThat(ASSIGNED.canTransitionTo(COMPLETED)).isFalse();
        assertThat(STARTED.canTransitionTo(ASSIGNED)).isFalse();
        assertThat(COMPLETED.isTerminal()).isTrue();
    }

    @Test
    void everyActiveStateCanFailOrCancel() {
        for (var active : new DeliveryPhysicalState[]{ASSIGNED, STARTED, ARRIVED, DELIVERING}) {
            assertThat(active.canTransitionTo(FAILED)).as("%s -> FAILED", active).isTrue();
            assertThat(active.canTransitionTo(CANCELLED)).as("%s -> CANCELLED", active).isTrue();
            assertThat(active.isTerminal()).isFalse();
        }
        assertThat(FAILED.isTerminal()).isTrue();
        assertThat(CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void mapsLegacyStatusesIntoTheMachine() {
        assertThat(DeliveryPhysicalState.fromLegacy(DeliveryStatus.SCHEDULED)).isEqualTo(ASSIGNED);
        assertThat(DeliveryPhysicalState.fromLegacy(DeliveryStatus.DISPATCHED)).isEqualTo(ASSIGNED);
        assertThat(DeliveryPhysicalState.fromLegacy(DeliveryStatus.DELIVERED)).isEqualTo(COMPLETED);
        assertThat(DeliveryPhysicalState.fromLegacy(DeliveryStatus.FAILED)).isEqualTo(FAILED);
    }

    @Test
    void mapsTheMachineBackToLegacyStatuses() {
        assertThat(ASSIGNED.toLegacyStatus()).isEqualTo(DeliveryStatus.DISPATCHED);
        assertThat(STARTED.toLegacyStatus()).isEqualTo(DeliveryStatus.DISPATCHED);
        assertThat(ARRIVED.toLegacyStatus()).isEqualTo(DeliveryStatus.DISPATCHED);
        assertThat(DELIVERING.toLegacyStatus()).isEqualTo(DeliveryStatus.DISPATCHED);
        assertThat(COMPLETED.toLegacyStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(FAILED.toLegacyStatus()).isEqualTo(DeliveryStatus.FAILED);
        // The legacy enum has no CANCELLED; a cancellation is reported as FAILED.
        assertThat(CANCELLED.toLegacyStatus()).isEqualTo(DeliveryStatus.FAILED);
    }
}
