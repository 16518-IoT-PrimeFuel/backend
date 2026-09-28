package com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.supply.domain.model.valueobjects.ReservationStatus;
import com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.entities.SupplyReservationPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplyReservationPersistenceRepository
        extends JpaRepository<SupplyReservationPersistenceEntity, Long> {

    List<SupplyReservationPersistenceEntity> findByProviderIdAndFuelProductIdAndStatus(
            Long providerId, Long fuelProductId, ReservationStatus status);

    List<SupplyReservationPersistenceEntity> findByReferenceAndStatus(String reference, ReservationStatus status);
}
