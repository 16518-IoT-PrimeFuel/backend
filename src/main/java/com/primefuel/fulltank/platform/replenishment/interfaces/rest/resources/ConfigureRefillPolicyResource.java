package com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources;

import jakarta.validation.constraints.Positive;

/**
 * Per-tank policy overrides. Every field is optional: omitting one keeps the approved global default
 * (U03 20% threshold / U04 10 points of hysteresis / full target).
 */
public record ConfigureRefillPolicyResource(
        @Positive Double lowLevelPercent,
        @Positive Double hysteresisPercent,
        @Positive Double targetLevelPercent,
        Long providerId,
        Long fuelProductId,
        Boolean autoGenerateEnabled) {
}
