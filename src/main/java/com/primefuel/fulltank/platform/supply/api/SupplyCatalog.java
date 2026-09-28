package com.primefuel.fulltank.platform.supply.api;

import java.util.Optional;

/**
 * Public seam over the inventory catalog. Other modules must read supply through this interface, never
 * through inventory internals (S11: delivery must not write the inventory repository).
 */
public interface SupplyCatalog {

    Optional<SupplySnapshot> findForTenant(Long providerId, Long fuelProductId);

    record SupplySnapshot(
            Long fuelProductId,
            Long providerId,
            String name,
            String fuelType,
            String unit,
            double pricePerUnit,
            double stock,
            boolean active) {
    }
}
