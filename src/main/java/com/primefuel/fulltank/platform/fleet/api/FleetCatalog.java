package com.primefuel.fulltank.platform.fleet.api;

import java.util.List;
import java.util.Optional;

/**
 * Read seam over the fleet catalog. Nothing outside the {@code fleet} module may read drivers or tankers
 * through their repositories or entities; this is the only public read surface (S12/T12-A). Snapshots are
 * immutable and carry the lifecycle flag so callers can reason about eligibility without touching the domain.
 */
public interface FleetCatalog {

    Optional<DriverSnapshot> findDriver(Long driverId);

    List<DriverSnapshot> listDrivers(Long providerId);

    Optional<TankerSnapshot> findTanker(Long tankerId);

    List<TankerSnapshot> listTankers(Long providerId);

    record DriverSnapshot(
            Long id,
            Long providerId,
            Long userId,
            String firstName,
            String lastName,
            String licenseNumber,
            String phoneNumber,
            String email,
            String status,
            boolean active) {
    }

    record TankerSnapshot(
            Long id,
            Long providerId,
            String licensePlate,
            String brand,
            String model,
            Double capacity,
            String unit,
            String status,
            boolean active) {
    }
}
