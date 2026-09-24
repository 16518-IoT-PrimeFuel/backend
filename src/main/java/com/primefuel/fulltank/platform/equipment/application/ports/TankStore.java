package com.primefuel.fulltank.platform.equipment.application.ports;

public interface TankStore {
    Long create(Long siteId, String name, String fuelType, Double capacity, String unit, Double currentLevel);

    boolean belongsToSite(Long tankId, Long siteId);

    boolean applyValidatedReading(Long tankId, double level, java.time.Instant capturedAt);
}
