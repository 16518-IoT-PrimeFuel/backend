package com.primefuel.fulltank.platform.fleet.api.events;

import java.time.Instant;

/**
 * Published when a fleet resource is disabled. The row is kept ({@code active=false}); consumers must
 * treat a disabled resource as not eligible. Part of the {@code api/events} contract (S12/T12-A).
 */
public record ResourceDisabledEvent(
        String resourceType,
        Long resourceId,
        Long providerId,
        Instant occurredAt) {
}
