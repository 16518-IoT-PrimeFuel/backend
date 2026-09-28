package com.primefuel.fulltank.platform.fleet.domain.model.commands;

import java.time.Instant;

/**
 * Holds a driver and a tanker for a temporal window at a requested volume (S13). {@code reference} is the
 * caller's idempotency key; uniqueness is wired by the {@code V18} migration, but the concurrent barrier that
 * uses it is T13-B.
 */
public record ReserveFleetCommand(
        Long providerId,
        Long driverId,
        Long tankerId,
        String reference,
        Instant windowStart,
        Instant windowEnd,
        Double volume,
        String unit) {
}
