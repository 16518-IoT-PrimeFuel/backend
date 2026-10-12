package com.primefuel.fulltank.platform.equipment.domain.model.commands;

import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.SiteType;

public record RegisterSiteCommand(
        Long organizationId,
        Long customerAccountId,
        String name,
        String address,
        Double latitude,
        Double longitude,
        SiteType siteType) {
    public RegisterSiteCommand(Long organizationId, Long customerAccountId, String name, String address) {
        this(organizationId, customerAccountId, name, address, null, null, null);
    }
}
