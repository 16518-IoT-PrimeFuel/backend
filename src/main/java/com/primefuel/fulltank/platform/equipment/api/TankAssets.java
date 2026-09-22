package com.primefuel.fulltank.platform.equipment.api;

import java.util.Optional;

public interface TankAssets {

    Optional<TankSnapshot> findById(Long tankId);

    Optional<Long> tankIdForLegacyEquipment(Long equipmentId);

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
