package com.primefuel.fulltank.platform.equipment.domain.model.valueobjects;

import java.util.List;

public record ProviderBuyerCompany(
        Long id,
        String name,
        String ruc,
        String sector,
        Long organizationId,
        long tankCount,
        long criticalTankCount,
        long activeOrderCount,
        long historicalOrderCount,
        List<Site> sites) {
    public record Site(Long id, Long customerAccountId, String name, String address) {}
}
