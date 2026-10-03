package com.primefuel.fulltank.platform.equipment.interfaces.rest.transform;

import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.ProviderTank;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.ProviderTankResource;

public final class ProviderTankResourceFromDomainAssembler {
    private ProviderTankResourceFromDomainAssembler() {}

    public static ProviderTankResource toResourceFromDomain(ProviderTank t) {
        return new ProviderTankResource(
                t.id(),
                t.buyerCompanyId(),
                t.organizationId(),
                t.customerAccountId(),
                t.siteId(),
                t.name(),
                t.siteName(),
                t.deliveryAddress(),
                t.fuelType(),
                t.fuelProductId(),
                t.capacity(),
                t.currentLevel(),
                t.unit(),
                t.levelPercent(),
                t.lowLevelPercent(),
                t.critical(),
                t.levelObservedAt(),
                t.levelSource(),
                t.devices().stream()
                        .map(
                                d ->
                                        new ProviderTankResource.DeviceResource(
                                                d.deviceId(), d.channel(), d.validFrom()))
                        .toList());
    }
}
