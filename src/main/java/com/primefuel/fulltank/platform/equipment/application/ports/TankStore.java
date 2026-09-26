package com.primefuel.fulltank.platform.equipment.application.ports;

public interface TankStore {
    Long create(Long siteId, String name, String fuelType, Double capacity, String unit, Double currentLevel);

    java.util.List<TankData> findBySiteId(Long siteId);

    java.util.Optional<TankData> findById(Long tankId);

    boolean belongsToSite(Long tankId, Long siteId);

    java.util.Optional<Double> capacity(Long tankId);

    boolean applyValidatedReading(Long tankId, double level, java.time.Instant capturedAt);
}
