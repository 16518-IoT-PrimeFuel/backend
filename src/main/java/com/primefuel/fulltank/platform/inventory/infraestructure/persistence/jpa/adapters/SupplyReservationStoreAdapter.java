package com.primefuel.fulltank.platform.inventory.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.inventory.application.ports.SupplyReservationStore;
import com.primefuel.fulltank.platform.inventory.infrastructure.persistence.jpa.entities.SupplyReservationPersistenceEntity;
import com.primefuel.fulltank.platform.inventory.infrastructure.persistence.jpa.repositories.FuelProductPersistenceRepository;
import com.primefuel.fulltank.platform.inventory.infrastructure.persistence.jpa.repositories.SupplyReservationJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class SupplyReservationStoreAdapter implements SupplyReservationStore {
    private final FuelProductPersistenceRepository products;
    private final SupplyReservationJpaRepository reservations;

    public SupplyReservationStoreAdapter(FuelProductPersistenceRepository products,
                                          SupplyReservationJpaRepository reservations) {
        this.products = products;
        this.reservations = reservations;
    }

    @Override
    @Transactional
    public boolean reserve(Long requestId, Long fuelProductId, Double quantity) {
        if (reservations.existsByRequestId(requestId)) return true;
        if (products.reserveStock(fuelProductId, quantity) != 1) return false;
        var reservation = new SupplyReservationPersistenceEntity();
        reservation.setRequestId(requestId);
        reservation.setFuelProductId(fuelProductId);
        reservation.setQuantity(quantity);
        reservation.setStatus("RESERVED");
        reservation.setReservedAt(Instant.now());
        reservations.save(reservation);
        return true;
    }
}
