package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentStatus;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;
import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.assemblers.ReplenishmentRequestPersistenceAssembler;
import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.repositories.ReplenishmentRequestPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ReplenishmentRequestRepositoryImpl implements ReplenishmentRequestRepository {

    private final ReplenishmentRequestPersistenceRepository persistenceRepository;

    public ReplenishmentRequestRepositoryImpl(ReplenishmentRequestPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<ReplenishmentRequest> findById(Long id) {
        return persistenceRepository.findById(id)
                .map(ReplenishmentRequestPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<ReplenishmentRequest> findByOrganizationId(Long organizationId) {
        return persistenceRepository.findByOrganizationId(organizationId).stream()
                .map(ReplenishmentRequestPersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public Optional<ReplenishmentRequest> findByEpisodeKey(String episodeKey) {
        return persistenceRepository.findByEpisodeKey(episodeKey)
                .map(ReplenishmentRequestPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public Optional<ReplenishmentRequest> findByOrderId(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return persistenceRepository.findByOrderId(orderId)
                .map(ReplenishmentRequestPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public Optional<ReplenishmentRequest> findPendingByTankId(Long tankId) {
        if (tankId == null) {
            return Optional.empty();
        }
        return persistenceRepository.findFirstByTankIdAndStatus(tankId, ReplenishmentStatus.PENDING)
                .map(ReplenishmentRequestPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public ReplenishmentRequest save(ReplenishmentRequest request) {
        var entity = ReplenishmentRequestPersistenceAssembler.toPersistenceFromDomain(request);
        return ReplenishmentRequestPersistenceAssembler.toDomainFromPersistence(persistenceRepository.save(entity));
    }

    @Override
    public ReplenishmentRequest saveAndFlush(ReplenishmentRequest request) {
        var entity = ReplenishmentRequestPersistenceAssembler.toPersistenceFromDomain(request);
        return ReplenishmentRequestPersistenceAssembler.toDomainFromPersistence(persistenceRepository.saveAndFlush(entity));
    }
}
