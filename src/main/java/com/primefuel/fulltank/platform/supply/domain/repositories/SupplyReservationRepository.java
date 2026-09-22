package com.primefuel.fulltank.platform.supply.domain.repositories;

import com.primefuel.fulltank.platform.supply.domain.model.aggregates.SupplyReservation;
import com.primefuel.fulltank.platform.supply.domain.model.valueobjects.ReservationStatus;

import java.util.List;

public interface SupplyReservationRepository {
    List<SupplyReservation> findActiveByProduct(Long providerId, Long fuelProductId);
    List<SupplyReservation> findByReferenceAndStatus(String reference, ReservationStatus status);
    SupplyReservation save(SupplyReservation reservation);
}
