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
 * The invariants of the tracking projection in isolation (S16/T16-A): a late sample never regresses the
 * latest trusted value — for positions <em>and</em> for load milestones (T16-B fix) — and a load milestone
 * cannot be reported out of order unless it is late (in which case it does not advance and is not validated).
 */
class DeliveryTrackingTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T10:05:00Z");
    private static final Instant T2 = Instant.parse("2026-10-01T10:10:00Z");

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
        assertThat(tracking.recordPosition(new GeoPosition(11.0, -65.0, null), T0)).isFalse();
        assertThat(tracking.getLastLatitude()).isEqualTo(10.0);
    }

    @Test
    void aLoadMilestoneCannotBeUnloadedBeforeLoading() {
        var tracking = new DeliveryTracking(1L, 10L, 100L);

        assertThatThrownBy(() -> tracking.recordLoad(LoadMilestone.UNLOADED, null, T0))
                .isInstanceOf(IllegalStateException.class);

        assertThat(tracking.recordLoad(LoadMilestone.LOADED, Volume.of(100.0, Unit.LITRE), T0)).isTrue();
        assertThat(tracking.isLoaded()).isTrue();
        assertThat(tracking.getLastLoadVolume()).isEqualTo(100.0);

        assertThat(tracking.recordLoad(LoadMilestone.UNLOADED, null, T1)).isTrue();
        assertThat(tracking.isLoaded()).isFalse();
        assertThat(tracking.getLastLoadMilestone()).isEqualTo(LoadMilestone.UNLOADED);
    }

    @Test
    void aLateLoadMilestoneDoesNotRegressTheLatest() {
        var tracking = new DeliveryTracking(1L, 10L, 100L);
        assertThat(tracking.recordLoad(LoadMilestone.LOADED, Volume.of(100.0, Unit.LITRE), T1)).isTrue();

        // Late (earlier recordedAt): returns false, mutates nothing.
        assertThat(tracking.recordLoad(LoadMilestone.UNLOADED, Volume.of(999.0, Unit.LITRE), T0)).isFalse();
        assertThat(tracking.isLoaded()).isTrue();
        assertThat(tracking.getLastLoadMilestone()).isEqualTo(LoadMilestone.LOADED);
        assertThat(tracking.getLastLoadAt()).isEqualTo(T1);
        assertThat(tracking.getLastLoadVolume()).isEqualTo(100.0);
        assertThat(tracking.getLastLoadUnit()).isEqualTo("LITRE");
    }

    @Test
    void aLateLoadThatWouldBreakTheSequenceIsNotRejected() {
        var tracking = new DeliveryTracking(1L, 10L, 100L);
        assertThat(tracking.recordLoad(LoadMilestone.LOADED, Volume.of(50.0, Unit.LITRE), T0)).isTrue();
        // Advances to unloaded; the latest is now T2.
        assertThat(tracking.recordLoad(LoadMilestone.UNLOADED, null, T2)).isTrue();
        assertThat(tracking.isLoaded()).isFalse();

        // A late UNLOADED whose recordedAt (T1) precedes the applied milestone would break the sequence if it
        // were validated (UNLOADED with loaded=false). Because it is late, it is neither validated nor throws:
        // it simply does not advance.
        assertThat(tracking.recordLoad(LoadMilestone.UNLOADED, null, T1)).isFalse();
        assertThat(tracking.isLoaded()).isFalse();
        assertThat(tracking.getLastLoadAt()).isEqualTo(T2);
        assertThat(tracking.getLastLoadMilestone()).isEqualTo(LoadMilestone.UNLOADED);
    }
}
