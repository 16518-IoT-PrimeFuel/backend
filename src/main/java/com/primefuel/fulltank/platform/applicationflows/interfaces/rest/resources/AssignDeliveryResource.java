package com.primefuel.fulltank.platform.applicationflows.interfaces.rest.resources;

import java.time.Instant;

/**
 * Request body for the v2 assignment. It carries no {@code providerId} on purpose: the provider is resolved
 * from the caller's principal. The order identifies the accepted replenishment request behind it.
 */
public record AssignDeliveryResource(
        String commandId,
        Long orderId,
        Long driverId,
        Long tankerId,
        Instant windowStart,
        Instant windowEnd,
        String scheduledDate,
        String notes) {
}
