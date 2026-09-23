package com.primefuel.fulltank.platform.tracking;

import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import com.primefuel.fulltank.platform.tracking.domain.model.aggregates.DeliveryTracking;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.GeoPosition;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two invariants of the tracking projection in isolation (S16/T16-A): a late sample never regresses the
 * latest trusted value, and a load milestone cannot be reported out of order.
 */
class DeliveryTrackingTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T10:05:00Z");

    @Test
    void aNewerSampleAdvancesTheLatestAndAnOlderOneDoesNot() {
        var tracking = new DeliveryTracking(1L, 10L, 100L);

        assertThat(tracking.recordPosition(new GeoPosition(10.0, -66.0, 5.0), T0)).isTrue();
        assertThat(tracking.getLastPositionAt()).isEqualTo(T0);

        // Newer: advances.
        assertThat(tracking.recordPosition(new GeoPosition(11.0, -67.0, 4.0), T1)).isTrue();
        assertThat(tracking.getLastLatitude()).isEqualTo(11.0);
        assertThat(tracking.getLastPositionAt()).isEqualTo(T1);

        // Late: preserved by the caller as raw evidence, but the projection stays on T1.
        assertThat(tracking.recordPosition(new GeoPosition(9.0, -65.0, 9.0), T0)).isFalse();
        assertThat(tracking.getLastLatitude()).isEqualTo(11.0);
        assertThat(tracking.getLastPositionAt()).isEqualTo(T1);
    }

    @Test
    void tiedTimestampDoesNotAdvanceTheLatest() {
        var tracking = new DeliveryTracking(1L, 10L, 100L);
        assertThat(tracking.recordPosition(new GeoPosition(10.0, -66.0, null), T0)).isTrue();
        assertThat(tracking.recordPosition(new GeoPosition(99.0, 99.0, null), T0)).isFalse();
        assertThat(tracking.getLastLatitude()).isEqualTo(10.0);
    }

    @Test
    void aLoadMilestoneCannotBeUnloadedBeforeLoading() {
        var tracking = new DeliveryTracking(1L, 10L, 100L);

        assertThatThrownBy(() -> tracking.recordLoad(LoadMilestone.UNLOADED, null, T0))
                .isInstanceOf(IllegalStateException.class);

        tracking.recordLoad(LoadMilestone.LOADED, Volume.of(100.0, Unit.LITRE), T0);
        assertThat(tracking.isLoaded()).isTrue();
        assertThat(tracking.getLastLoadVolume()).isEqualTo(100.0);

        tracking.recordLoad(LoadMilestone.UNLOADED, null, T1);
        assertThat(tracking.isLoaded()).isFalse();
        assertThat(tracking.getLastLoadMilestone()).isEqualTo(LoadMilestone.UNLOADED);
    }
}
