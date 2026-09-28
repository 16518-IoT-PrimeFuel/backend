package com.primefuel.fulltank.platform.fleet.api.events;

import java.time.Instant;

/**
 * Published when a fleet resource is (re-)enabled. Part of the versioned {@code api/events} contract
 * (S12/T12-A); no secrets or PII beyond the resource identity.
 */
public record ResourceEnabledEvent(
        String resourceType,
        Long resourceId,
        Long providerId,
        Instant occurredAt) {

    public static final String DRIVER = "DRIVER";
    public static final String TANKER = "TANKER";
}
