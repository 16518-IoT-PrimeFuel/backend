package com.primefuel.fulltank.platform.fleet;

import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.ReservationWindow;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T13-A: the half-open window semantics of {@link ReservationWindow} — the boundary cases that decide
 * whether two reservations overlap.
 */
class ReservationWindowTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-10-01T11:00:00Z");

    @Test
    void rejectsAWindowThatDoesNotStartBeforeItEnds() {
        assertThatThrownBy(() -> new ReservationWindow(T1, T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationWindow(T2, T1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMissingBoundary() {
        assertThatThrownBy(() -> new ReservationWindow(null, T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationWindow(T0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partiallyOverlappingWindowsOverlap() {
        assertThat(new ReservationWindow(T0, T2).overlaps(new ReservationWindow(T1, T3))).isTrue();
        assertThat(new ReservationWindow(T1, T3).overlaps(new ReservationWindow(T0, T2))).isTrue();
    }

    @Test
    void aWindowContainedInAnotherOverlaps() {
        assertThat(new ReservationWindow(T0, T3).overlaps(new ReservationWindow(T1, T2))).isTrue();
        assertThat(new ReservationWindow(T1, T2).overlaps(new ReservationWindow(T0, T3))).isTrue();
    }

    @Test
    void touchingWindowsDoNotOverlap() {
        // [08,09) and [09,10) are back-to-back, not overlapping.
        assertThat(new ReservationWindow(T0, T1).overlaps(new ReservationWindow(T1, T2))).isFalse();
        assertThat(new ReservationWindow(T1, T2).overlaps(new ReservationWindow(T0, T1))).isFalse();
    }

    @Test
    void disjointWindowsDoNotOverlap() {
        assertThat(new ReservationWindow(T0, T1).overlaps(new ReservationWindow(T2, T3))).isFalse();
    }
}
