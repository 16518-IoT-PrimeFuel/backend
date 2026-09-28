package com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.supply.domain.model.aggregates.SupplyReservation;
import com.primefuel.fulltank.platform.supply.domain.model.valueobjects.ReservationStatus;
import com.primefuel.fulltank.platform.supply.domain.repositories.SupplyReservationRepository;
import com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.assemblers.SupplyReservationPersistenceAssembler;
import com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.repositories.SupplyReservationPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SupplyReservationRepositoryImpl implements SupplyReservationRepository {

    private final SupplyReservationPersistenceRepository persistenceRepository;

    public SupplyReservationRepositoryImpl(SupplyReservationPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public List<SupplyReservation> findActiveByProduct(Long providerId, Long fuelProductId) {
        return persistenceRepository
                .findByProviderIdAndFuelProductIdAndStatus(providerId, fuelProductId, ReservationStatus.ACTIVE)
                .stream().map(SupplyReservationPersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public List<SupplyReservation> findByReferenceAndStatus(String reference, ReservationStatus status) {
        return persistenceRepository.findByReferenceAndStatus(reference, status).stream()
                .map(SupplyReservationPersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public SupplyReservation save(SupplyReservation reservation) {
        var entity = SupplyReservationPersistenceAssembler.toPersistenceFromDomain(reservation);
        return SupplyReservationPersistenceAssembler.toDomainFromPersistence(persistenceRepository.save(entity));
    }
}
