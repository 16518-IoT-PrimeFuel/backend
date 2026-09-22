package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

public record TankResource(
        Long id,
        Long organizationId,
        Long customerAccountId,
        Long siteId,
        String name,
        String fuelType,
        String unit,
        double capacity,
        double currentLevel,
        int configurationVersion,
        String levelSource,
        boolean active) {
}
