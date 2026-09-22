package com.primefuel.fulltank.platform.equipment.domain.model.commands;

public record UpdateTankConfigurationCommand(
        Long tankId,
        String fuelType,
        Double capacity,
        String unit) {
}
