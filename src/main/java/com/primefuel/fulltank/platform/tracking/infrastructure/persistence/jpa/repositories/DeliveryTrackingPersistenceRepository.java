package com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.entities.DeliveryTrackingPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryTrackingPersistenceRepository
        extends JpaRepository<DeliveryTrackingPersistenceEntity, Long> {

    /** Backed by {@code uk_delivery_tracking_delivery}, so at most one row can match. */
    Optional<DeliveryTrackingPersistenceEntity> findByDeliveryId(Long deliveryId);

    long deleteByDeliveryId(Long deliveryId);
}
