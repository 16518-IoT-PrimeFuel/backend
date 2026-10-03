package com.primefuel.fulltank.platform.equipment.domain.model.commands;

public record UpdateProviderTankCommand(
        Long providerId,
        Long tankId,
        Long fuelProductId,
        Double lowLevelPercent,
        String deviceId,
        String channel,
        Boolean autoGenerateEnabled) {}
