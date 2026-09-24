package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.application.ports.TankStore;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TankPersistenceEntity;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.TankJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class TankStoreAdapter implements TankStore {
    private final TankJpaRepository repository;

    public TankStoreAdapter(TankJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Long create(Long siteId, String name, String fuelType, Double capacity, String unit, Double currentLevel) {
        var tank = new TankPersistenceEntity();
        tank.setCustomerSiteId(siteId);
        tank.setName(name);
        tank.setFuelType(fuelType);
        tank.setCapacity(capacity);
        tank.setUnit(unit);
        tank.setCurrentLevel(currentLevel);
        tank.setStatus("ACTIVE");
        return repository.save(tank).getId();
    }
}
