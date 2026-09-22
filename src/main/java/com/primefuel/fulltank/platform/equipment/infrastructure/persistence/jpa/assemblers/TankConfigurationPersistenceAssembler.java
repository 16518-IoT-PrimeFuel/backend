package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.TankConfiguration;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TankConfigurationPersistenceEntity;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;

public final class TankConfigurationPersistenceAssembler {

    private TankConfigurationPersistenceAssembler() {
    }

    public static TankConfiguration toDomainFromPersistence(TankConfigurationPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new TankConfiguration();
        domain.setId(entity.getId());
        domain.setTankId(entity.getTankId());
        domain.setVersion(entity.getVersion());
        domain.setFuelType(entity.getFuelType());
        domain.setCapacity(new Volume(entity.getCapacityAmount(), Unit.valueOf(entity.getCapacityUnit())));
        domain.setRecordedAt(entity.getRecordedAt());
        return domain;
    }

    public static TankConfigurationPersistenceEntity toPersistenceFromDomain(TankConfiguration domain) {
        if (domain == null) return null;
        var entity = new TankConfigurationPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setTankId(domain.getTankId());
        entity.setVersion(domain.getVersion());
        entity.setFuelType(domain.getFuelType());
        entity.setCapacityAmount(domain.getCapacity().amount());
        entity.setCapacityUnit(domain.getCapacity().unit().name());
        entity.setRecordedAt(domain.getRecordedAt());
        return entity;
    }
}
