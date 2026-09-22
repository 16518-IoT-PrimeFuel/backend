package com.primefuel.fulltank.platform.replenishment.domain.model.commands;

/**
 * Configures the low-level policy for one tank. Every threshold is optional: omitting it keeps the
 * approved global default (U03 20% / U04 10 points of hysteresis).
 */
public record ConfigureRefillPolicyCommand(
        Long tankId,
        Long organizationId,
        Double lowLevelPercent,
        Double hysteresisPercent,
        Double targetLevelPercent,
        Long providerId,
        Long fuelProductId,
        Boolean autoGenerateEnabled) {
}
