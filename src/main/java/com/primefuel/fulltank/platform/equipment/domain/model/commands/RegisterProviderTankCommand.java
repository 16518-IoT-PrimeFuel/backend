package com.primefuel.fulltank.platform.equipment.domain.model.commands;

public record RegisterProviderTankCommand(
        Long providerId,
        Long buyerCompanyId,
        Long customerAccountId,
        Long siteId,
        String name,
        Long fuelProductId,
        Double capacity,
        String unit,
        Double initialLevel,
        double lowLevelPercent,
        String deviceId,
        String channel,
        boolean autoGenerateEnabled) {}
