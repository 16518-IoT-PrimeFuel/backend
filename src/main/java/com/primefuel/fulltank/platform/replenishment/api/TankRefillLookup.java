package com.primefuel.fulltank.platform.replenishment.api;

import java.util.Optional;

public interface TankRefillLookup {
    Optional<PolicySnapshot> findPolicy(Long tankId);

    record PolicySnapshot(
            Long organizationId,
            double lowLevelPercent,
            Long fuelProductId,
            double hysteresisPercent,
            double targetLevelPercent,
            boolean autoGenerateEnabled,
            Long providerId) {}

    double defaultLowLevelPercent();
}
