package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.SiteType;

public record SiteResource(
        Long id,
        Long organizationId,
        Long customerAccountId,
        String name,
        String address,
        boolean active,
        Double latitude,
        Double longitude,
        SiteType siteType) {
}
