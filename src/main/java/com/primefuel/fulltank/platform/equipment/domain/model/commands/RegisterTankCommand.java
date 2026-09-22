package com.primefuel.fulltank.platform.equipment.domain.model.commands;

public record RegisterTankCommand(
        Long organizationId,
        Long customerAccountId,
        Long siteId,
        String name,
        String fuelType,
        Double capacity,
        String unit,
        Double initialLevel,
        Long legacyEquipmentId) {
}
