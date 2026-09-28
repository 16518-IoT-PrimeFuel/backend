package com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.Tanker;
import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.entities.TankerPersistenceEntity;

public final class TankerPersistenceAssembler {

    private TankerPersistenceAssembler() {
    }

    public static Tanker toDomain(TankerPersistenceEntity entity) {
        var tanker = new Tanker();
        tanker.setId(entity.getId());
        tanker.setProviderId(entity.getProviderId());
        tanker.setLicensePlate(entity.getLicensePlate());
        tanker.setBrand(entity.getBrand());
        tanker.setModel(entity.getModel());
        tanker.setCapacity(entity.getCapacity());
        tanker.setUnit(entity.getUnit());
        tanker.setStatus(entity.getStatus());
        tanker.setActive(entity.isActive());
        tanker.setDeactivatedAt(entity.getDeactivatedAt());
        return tanker;
    }

    public static TankerPersistenceEntity toPersistence(Tanker tanker) {
        var entity = new TankerPersistenceEntity();
        if (tanker.getId() != null) entity.setId(tanker.getId());
        entity.setProviderId(tanker.getProviderId());
        entity.setLicensePlate(tanker.getLicensePlate());
        entity.setBrand(tanker.getBrand());
        entity.setModel(tanker.getModel());
        entity.setCapacity(tanker.getCapacity());
        entity.setUnit(tanker.getUnit());
        entity.setStatus(tanker.getStatus());
        entity.setActive(tanker.isActive());
        entity.setDeactivatedAt(tanker.getDeactivatedAt());
        return entity;
    }
}
