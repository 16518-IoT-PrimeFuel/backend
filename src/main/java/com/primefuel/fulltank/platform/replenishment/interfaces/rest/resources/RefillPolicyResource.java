package com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources;

public record RefillPolicyResource(
        Long id,
        Long tankId,
        Long organizationId,
        double lowLevelPercent,
        double hysteresisPercent,
        double targetLevelPercent,
        Long providerId,
        Long fuelProductId,
        boolean autoGenerateEnabled,
        int policyVersion,
        int version) {
}
