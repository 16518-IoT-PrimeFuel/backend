package com.primefuel.fulltank.platform.equipment.api;

import java.time.Instant;
import java.util.Optional;

public interface TankAssets {

    Optional<TankSnapshot> findById(Long tankId);

    Optional<Long> tankIdForLegacyEquipment(Long equipmentId);

    /**
     * Applies a telemetry-sourced level to a tank. Returns {@code false} when the observation is not newer
     * than the current one, so a snapshot never regresses on out-of-order input.
     */
    boolean applyValidatedReading(Long tankId, double level, String unit, Instant observedAt);

    record TankSnapshot(
            Long id,
            Long organizationId,
            Long customerAccountId,
            Long siteId,
            String fuelType,
            String unit,
            double capacity,
            double currentLevel,
            int configurationVersion,
            boolean active) {
    }
}
