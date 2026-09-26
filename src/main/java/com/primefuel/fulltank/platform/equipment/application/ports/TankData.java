package com.primefuel.fulltank.platform.equipment.application.ports;

import java.time.Instant;

public record TankData(Long id, Long siteId, String name, String fuelType,
                       Double capacity, String unit, Double currentLevel,
                       String status, Instant lastReadingAt) {
}
