package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;

public record ProviderTankResource(
        Long id,
        Long buyerCompanyId,
        Long organizationId,
        Long customerAccountId,
        Long siteId,
        String name,
        String siteName,
        String deliveryAddress,
        String fuelType,
        Long fuelProductId,
        double capacity,
        double currentLevel,
        String unit,
        double levelPercent,
        double lowLevelPercent,
        boolean critical,
        Instant levelObservedAt,
        String levelSource,
        List<DeviceResource> devices) {
    public record DeviceResource(String deviceId, String channel, Instant validFrom) {}
}
