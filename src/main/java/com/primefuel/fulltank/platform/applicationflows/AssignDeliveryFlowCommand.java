package com.primefuel.fulltank.platform.applicationflows;

import java.time.Instant;

/**
 * The S15/T15-A assignment command: hold real resources for an accepted need and materialise an assigned
 * delivery. {@code commandId} is the caller's correlation/idempotency key — a retry with the same key is a
 * no-op that returns the same delivery. {@code providerId} must be resolved from the principal by the
 * caller (never trusted from a request body), and the orchestrator verifies it owns the request behind
 * {@code orderId}.
 */
public record AssignDeliveryFlowCommand(
        String commandId,
        Long orderId,
        Long providerId,
        Long driverId,
        Long tankerId,
        Instant windowStart,
        Instant windowEnd,
        String scheduledDate,
        String notes) {
}
