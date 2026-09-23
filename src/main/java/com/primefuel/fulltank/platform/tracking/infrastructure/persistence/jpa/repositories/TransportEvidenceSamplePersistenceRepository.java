package com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.entities.TransportEvidenceSamplePersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransportEvidenceSamplePersistenceRepository
        extends JpaRepository<TransportEvidenceSamplePersistenceEntity, Long> {

    List<TransportEvidenceSamplePersistenceEntity> findByDeliveryIdOrderByReceivedAtAsc(Long deliveryId);

    long countByDeliveryId(Long deliveryId);
}
