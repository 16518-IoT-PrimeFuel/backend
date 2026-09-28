package com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.Driver;
import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.entities.DriverPersistenceEntity;

public final class DriverPersistenceAssembler {

    private DriverPersistenceAssembler() {
    }

    public static Driver toDomain(DriverPersistenceEntity entity) {
        var driver = new Driver();
        driver.setId(entity.getId());
        driver.setProviderId(entity.getProviderId());
        driver.setUserId(entity.getUserId());
        driver.setFirstName(entity.getFirstName());
        driver.setLastName(entity.getLastName());
        driver.setLicenseNumber(entity.getLicenseNumber());
        driver.setPhoneNumber(entity.getPhoneNumber());
        driver.setEmail(entity.getEmail());
        driver.setStatus(entity.getStatus());
        driver.setActive(entity.isActive());
        driver.setDeactivatedAt(entity.getDeactivatedAt());
        return driver;
    }

    public static DriverPersistenceEntity toPersistence(Driver driver) {
        var entity = new DriverPersistenceEntity();
        if (driver.getId() != null) entity.setId(driver.getId());
        entity.setProviderId(driver.getProviderId());
        entity.setUserId(driver.getUserId());
        entity.setFirstName(driver.getFirstName());
        entity.setLastName(driver.getLastName());
        entity.setLicenseNumber(driver.getLicenseNumber());
        entity.setPhoneNumber(driver.getPhoneNumber());
        entity.setEmail(driver.getEmail());
        entity.setStatus(driver.getStatus());
        entity.setActive(driver.isActive());
        entity.setDeactivatedAt(driver.getDeactivatedAt());
        return entity;
    }
}
