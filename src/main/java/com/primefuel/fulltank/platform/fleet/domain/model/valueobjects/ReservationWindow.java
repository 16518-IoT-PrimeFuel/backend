package com.primefuel.fulltank.platform.fleet.domain.model.valueobjects;

import java.time.Instant;

/**
 * A half-open reservation window {@code [start, end)}. Two windows overlap when each starts strictly before
 * the other ends, so windows that merely touch ({@code a.end == b.start}) do <em>not</em> overlap and
 * back-to-back reservations are allowed (S13: "sin overlap").
 */
public record ReservationWindow(Instant start, Instant end) {

    public ReservationWindow {
        if (start == null || end == null) {
            throw new IllegalArgumentException("A reservation window requires a start and an end");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("A reservation window must start before it ends");
        }
    }

    public boolean overlaps(ReservationWindow other) {
        return start.isBefore(other.end) && end.isAfter(other.start);
    }
}
