package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import java.time.Instant;

public record TankStateResource(Long id, Long siteId, String name, String fuelType,
                                Double capacity, String unit, Double currentLevel,
                                String status, Instant lastReadingAt) {
}
