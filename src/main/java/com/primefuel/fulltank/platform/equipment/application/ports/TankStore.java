package com.primefuel.fulltank.platform.equipment.application.ports;

public interface TankStore {
    Long create(Long siteId, String name, String fuelType, Double capacity, String unit, Double currentLevel);
}
