package com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.FleetReservation;
import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.FleetReservationStatus;
import com.primefuel.fulltank.platform.fleet.domain.repositories.FleetReservationRepository;
import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.assemblers.FleetReservationPersistenceAssembler;
import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.repositories.FleetReservationPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class FleetReservationRepositoryImpl implements FleetReservationRepository {

    private final FleetReservationPersistenceRepository persistenceRepository;

    public FleetReservationRepositoryImpl(FleetReservationPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public FleetReservation save(FleetReservation reservation) {
        return FleetReservationPersistenceAssembler.toDomain(
                persistenceRepository.save(FleetReservationPersistenceAssembler.toPersistence(reservation)));
    }

    @Override
    public Optional<FleetReservation> findById(Long id) {
        return persistenceRepository.findById(id).map(FleetReservationPersistenceAssembler::toDomain);
    }

    @Override
    public List<FleetReservation> findActiveByProvider(Long providerId) {
        return persistenceRepository.findByProviderId(providerId).stream()
                .map(FleetReservationPersistenceAssembler::toDomain)
                .filter(FleetReservation::isActive)
                .toList();
    }

    @Override
    public List<FleetReservation> findActiveOverlapping(Long providerId,
                                                        Long driverId,
                                                        Long tankerId,
                                                        Instant windowStart,
                                                        Instant windowEnd) {
        return persistenceRepository.findActiveOverlapping(providerId, driverId, tankerId,
                        FleetReservationStatus.ACTIVE, windowStart, windowEnd).stream()
                .map(FleetReservationPersistenceAssembler::toDomain)
                .toList();
    }
}
