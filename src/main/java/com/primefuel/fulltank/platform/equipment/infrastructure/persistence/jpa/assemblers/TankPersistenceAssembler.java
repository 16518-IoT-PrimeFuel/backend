package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TankPersistenceEntity;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;

public final class TankPersistenceAssembler {

    private TankPersistenceAssembler() {
    }

    public static Tank toDomainFromPersistence(TankPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new Tank();
        domain.setId(entity.getId());
        domain.setOrganizationId(entity.getOrganizationId());
        domain.setCustomerAccountId(entity.getCustomerAccountId());
        domain.setSiteId(entity.getSiteId());
        domain.setName(entity.getName());
        domain.setFuelType(entity.getFuelType());
        domain.setClassification(entity.getClassification());
        domain.setLegacyEquipmentId(entity.getLegacyEquipmentId());
        domain.setConfigurationVersion(entity.getConfigurationVersion());
        domain.setCapacity(new Volume(entity.getCapacityAmount(), Unit.valueOf(entity.getCapacityUnit())));
        domain.setCurrentLevel(new Volume(entity.getLevelAmount(), Unit.valueOf(entity.getLevelUnit())));
        domain.setLevelObservedAt(entity.getLevelObservedAt());
        domain.setLevelSource(entity.getLevelSource());
        domain.setActive(entity.isActive());
        return domain;
    }

    public static TankPersistenceEntity toPersistenceFromDomain(Tank domain) {
        if (domain == null) return null;
        var entity = new TankPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setOrganizationId(domain.getOrganizationId());
        entity.setCustomerAccountId(domain.getCustomerAccountId());
        entity.setSiteId(domain.getSiteId());
        entity.setName(domain.getName());
        entity.setFuelType(domain.getFuelType());
        entity.setClassification(domain.getClassification());
        entity.setLegacyEquipmentId(domain.getLegacyEquipmentId());
        entity.setConfigurationVersion(domain.getConfigurationVersion());
        entity.setCapacityAmount(domain.getCapacity().amount());
        entity.setCapacityUnit(domain.getCapacity().unit().name());
        entity.setLevelAmount(domain.getCurrentLevel().amount());
        entity.setLevelUnit(domain.getCurrentLevel().unit().name());
        entity.setLevelObservedAt(domain.getLevelObservedAt());
        entity.setLevelSource(domain.getLevelSource());
        entity.setActive(domain.isActive());
        return entity;
    }
}
