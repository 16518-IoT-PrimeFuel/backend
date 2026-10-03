package com.primefuel.fulltank.platform.inventory.api;

import java.util.Optional;

public interface FuelProductLookup {
    Optional<ProductSnapshot> findById(Long id);

    record ProductSnapshot(
            Long id, Long providerId, String unit, String fuelType, boolean active) {}
}
