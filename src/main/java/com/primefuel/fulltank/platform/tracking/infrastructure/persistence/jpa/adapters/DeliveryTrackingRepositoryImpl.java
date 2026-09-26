package com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.tracking.domain.model.aggregates.DeliveryTracking;
import com.primefuel.fulltank.platform.tracking.domain.repositories.DeliveryTrackingRepository;
import com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.assemblers.DeliveryTrackingPersistenceAssembler;
import com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.repositories.DeliveryTrackingPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class DeliveryTrackingRepositoryImpl implements DeliveryTrackingRepository {

    private final DeliveryTrackingPersistenceRepository persistenceRepository;

    public DeliveryTrackingRepositoryImpl(DeliveryTrackingPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<DeliveryTracking> findByDeliveryId(Long deliveryId) {
        return persistenceRepository.findByDeliveryId(deliveryId)
                .map(DeliveryTrackingPersistenceAssembler::toDomain);
    }

    @Override
    public DeliveryTracking save(DeliveryTracking tracking) {
        // saveAndFlush so an optimistic-lock conflict surfaces inside the recorder's transaction (same
        // pattern as the delivery lifecycle), instead of at commit time.
        return DeliveryTrackingPersistenceAssembler.toDomain(
                persistenceRepository.saveAndFlush(DeliveryTrackingPersistenceAssembler.toPersistence(tracking)));
    }

    @Override
    public long deleteByDeliveryId(Long deliveryId) {
        return persistenceRepository.deleteByDeliveryId(deliveryId);
    }
}
