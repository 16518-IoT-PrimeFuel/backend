package com.primefuel.fulltank.platform.equipment.interfaces.rest.transform;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.TankResource;

public final class TankResourceFromDomainAssembler {

    private TankResourceFromDomainAssembler() {
    }

    public static TankResource toResourceFromDomain(Tank tank) {
        return new TankResource(
                tank.getId(),
                tank.getOrganizationId(),
                tank.getCustomerAccountId(),
                tank.getSiteId(),
                tank.getName(),
                tank.getFuelType(),
                tank.getCapacity().unit().name(),
                tank.getCapacity().amount(),
                tank.getCurrentLevel().amount(),
                tank.getConfigurationVersion(),
                tank.getLevelSource(),
                tank.isActive());
    }
}
