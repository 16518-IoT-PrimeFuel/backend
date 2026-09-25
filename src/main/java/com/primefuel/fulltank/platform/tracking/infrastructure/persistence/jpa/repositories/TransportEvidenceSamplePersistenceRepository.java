package com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.entities.TransportEvidenceSamplePersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransportEvidenceSamplePersistenceRepository
        extends JpaRepository<TransportEvidenceSamplePersistenceEntity, Long> {

    List<TransportEvidenceSamplePersistenceEntity> findByDeliveryIdOrderByReceivedAtAsc(Long deliveryId);

    /**
     * Chronological (device-clock) order with reception order (and id) as a deterministic tie-breaker, so the
     * timeline is stable even when two samples carry the same {@code recordedAt}.
     */
    List<TransportEvidenceSamplePersistenceEntity> findByDeliveryIdOrderByRecordedAtAscReceivedAtAscIdAsc(
            Long deliveryId);

    long countByDeliveryId(Long deliveryId);

    Optional<TransportEvidenceSamplePersistenceEntity> findByDeliveryIdAndClientEventId(Long deliveryId,
                                                                                      String clientEventId);
}
