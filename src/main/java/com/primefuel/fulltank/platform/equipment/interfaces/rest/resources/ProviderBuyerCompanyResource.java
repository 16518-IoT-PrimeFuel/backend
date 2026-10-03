package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import java.util.List;

public record ProviderBuyerCompanyResource(
        Long id,
        String name,
        String ruc,
        String sector,
        Long organizationId,
        long tankCount,
        long criticalTankCount,
        long activeOrderCount,
        long historicalOrderCount,
        List<SiteResource> sites) {
    public record SiteResource(Long id, Long customerAccountId, String name, String address) {}
}
