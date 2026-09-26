package com.primefuel.fulltank.platform.fulfillment;

import com.primefuel.fulltank.platform.fulfillment.application.ports.FleetReservation;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.adapters.FleetReservationStoreAdapter;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DriverPersistenceEntity;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.VehiclePersistenceEntity;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DriverPersistenceRepository;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.FleetReservationPersistenceRepository;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.VehiclePersistenceRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FleetReservationStoreTest {
    private final DriverPersistenceRepository drivers = mock(DriverPersistenceRepository.class);
    private final VehiclePersistenceRepository vehicles = mock(VehiclePersistenceRepository.class);
    private final FleetReservationPersistenceRepository reservations = mock(FleetReservationPersistenceRepository.class);
    private final FleetReservationStoreAdapter store = new FleetReservationStoreAdapter(drivers, vehicles, reservations);
    private final Instant start = Instant.parse("2026-09-25T10:00:00Z");
    private final FleetReservation request = new FleetReservation("delivery:1", 2L, null, 3L, 4L,
            start, start.plusSeconds(3600), 100D);

    @Test
    void reservesOnlyWhenLockedResourcesHaveNoOverlap() {
        var driver = new DriverPersistenceEntity();
        driver.setId(3L);
        driver.setProviderId(2L);
        driver.setStatus("AVAILABLE");
        var vehicle = new VehiclePersistenceEntity();
        vehicle.setId(4L);
        vehicle.setProviderId(2L);
        vehicle.setStatus("AVAILABLE");
        vehicle.setEnabled(true);
        vehicle.setCapacity(100D);
        when(reservations.findByIdempotencyKey("delivery:1")).thenReturn(Optional.empty());
        when(drivers.findByIdForUpdate(3L)).thenReturn(Optional.of(driver));
        when(vehicles.findByIdForUpdate(4L)).thenReturn(Optional.of(vehicle));
        when(reservations.countOverlapping(3L, 4L, start, start.plusSeconds(3600))).thenReturn(0L);

        assertTrue(store.reserve(request));
        verify(reservations).save(any());
    }

    @Test
    void retryIsIdempotentAndOverlapIsRejected() {
        var existing = new com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.FleetReservationPersistenceEntity();
        existing.setStatus("RESERVED");
        when(reservations.findByIdempotencyKey("delivery:1")).thenReturn(Optional.of(existing));
        assertTrue(store.reserve(request));
        verifyNoInteractions(drivers, vehicles);

        reset(reservations);
        when(reservations.findByIdempotencyKey("delivery:1")).thenReturn(Optional.empty());
        var driver = new DriverPersistenceEntity();
        driver.setProviderId(2L);
        driver.setStatus("AVAILABLE");
        var vehicle = new VehiclePersistenceEntity();
        vehicle.setProviderId(2L);
        vehicle.setStatus("AVAILABLE");
        vehicle.setEnabled(true);
        vehicle.setCapacity(100D);
        when(drivers.findByIdForUpdate(3L)).thenReturn(Optional.of(driver));
        when(vehicles.findByIdForUpdate(4L)).thenReturn(Optional.of(vehicle));
        when(reservations.countOverlapping(any(), any(), any(), any())).thenReturn(1L);
        assertFalse(store.reserve(request));
        verify(reservations, never()).save(any());
    }
}
