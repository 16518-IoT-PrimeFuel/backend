package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.fulfillment.application.ports.FleetReservation;
import com.primefuel.fulltank.platform.fulfillment.application.ports.FleetReservationStore;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.FleetReservationPersistenceEntity;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DriverPersistenceRepository;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.FleetReservationPersistenceRepository;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.VehiclePersistenceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Component
public class FleetReservationStoreAdapter implements FleetReservationStore {
    private final DriverPersistenceRepository drivers;
    private final VehiclePersistenceRepository vehicles;
    private final FleetReservationPersistenceRepository reservations;
    private final Clock clock = Clock.systemUTC();

    public FleetReservationStoreAdapter(DriverPersistenceRepository drivers,
                                        VehiclePersistenceRepository vehicles,
                                        FleetReservationPersistenceRepository reservations) {
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.reservations = reservations;
    }

    @Override
    @Transactional
    public boolean reserve(FleetReservation request) {
        var existing = reservations.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) return "RESERVED".equals(existing.get().getStatus());
        if (request.windowStart() == null || request.windowEnd() == null
                || !request.windowEnd().isAfter(request.windowStart()) || request.volume() == null
                || request.volume() <= 0) return false;

        // Lock both resource rows before checking overlap; every caller uses this order.
        var driver = drivers.findByIdForUpdate(request.driverId()).orElse(null);
        var vehicle = vehicles.findByIdForUpdate(request.vehicleId()).orElse(null);
        if (driver == null || vehicle == null || !request.providerId().equals(driver.getProviderId())
                || !request.providerId().equals(vehicle.getProviderId())
                || !"AVAILABLE".equalsIgnoreCase(driver.getStatus())
                || !"AVAILABLE".equalsIgnoreCase(vehicle.getStatus())
                || !vehicle.isEnabled() || vehicle.getCapacity() < request.volume()
                || (driver.getLicenseExpiresAt() != null
                && driver.getLicenseExpiresAt().isBefore(LocalDate.now(clock)))) return false;
        if (reservations.countOverlapping(request.driverId(), request.vehicleId(),
                request.windowStart(), request.windowEnd()) > 0) return false;

        var entity = new FleetReservationPersistenceEntity();
        entity.setProviderId(request.providerId());
        entity.setDeliveryId(request.deliveryId());
        entity.setDriverId(request.driverId());
        entity.setVehicleId(request.vehicleId());
        entity.setWindowStart(request.windowStart());
        entity.setWindowEnd(request.windowEnd());
        entity.setVolume(request.volume());
        entity.setStatus("RESERVED");
        entity.setIdempotencyKey(request.idempotencyKey());
        entity.setCreatedAt(Instant.now(clock));
        reservations.save(entity);
        return true;
    }

    @Override
    @Transactional
    public boolean release(Long providerId, String idempotencyKey) {
        var existing = reservations.findByIdempotencyKey(idempotencyKey);
        if (existing.isEmpty() || !providerId.equals(existing.get().getProviderId())) return false;
        var entity = existing.get();
        if ("RELEASED".equals(entity.getStatus())) return true;
        entity.setStatus("RELEASED");
        entity.setReleasedAt(Instant.now(clock));
        reservations.save(entity);
        return true;
    }
}
