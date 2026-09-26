package com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.tracking.domain.model.entities.TransportEvidenceSample;
import com.primefuel.fulltank.platform.tracking.domain.repositories.TransportEvidenceSampleRepository;
import com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.assemblers.TransportEvidenceSamplePersistenceAssembler;
import com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.repositories.TransportEvidenceSamplePersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TransportEvidenceSampleRepositoryImpl implements TransportEvidenceSampleRepository {

    private final TransportEvidenceSamplePersistenceRepository persistenceRepository;

    public TransportEvidenceSampleRepositoryImpl(
            TransportEvidenceSamplePersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public TransportEvidenceSample save(TransportEvidenceSample sample) {
        return TransportEvidenceSamplePersistenceAssembler.toDomain(
                persistenceRepository.save(TransportEvidenceSamplePersistenceAssembler.toPersistence(sample)));
    }

    @Override
    public List<TransportEvidenceSample> findByDeliveryId(Long deliveryId) {
        return persistenceRepository.findByDeliveryIdOrderByReceivedAtAsc(deliveryId).stream()
                .map(TransportEvidenceSamplePersistenceAssembler::toDomain)
                .toList();
    }

    @Override
    public List<TransportEvidenceSample> findByDeliveryIdOrderedByRecordedAt(Long deliveryId) {
        return persistenceRepository.findByDeliveryIdOrderByRecordedAtAscReceivedAtAscIdAsc(deliveryId).stream()
                .map(TransportEvidenceSamplePersistenceAssembler::toDomain)
                .toList();
    }

    @Override
    public long countByDeliveryId(Long deliveryId) {
        return persistenceRepository.countByDeliveryId(deliveryId);
    }

    @Override
    public long deleteByDeliveryId(Long deliveryId) {
        return persistenceRepository.deleteByDeliveryId(deliveryId);
    }

    @Override
    public Optional<TransportEvidenceSample> findByDeliveryIdAndClientEventId(Long deliveryId, String clientEventId) {
        return persistenceRepository.findByDeliveryIdAndClientEventId(deliveryId, clientEventId)
                .map(TransportEvidenceSamplePersistenceAssembler::toDomain);
    }
}
