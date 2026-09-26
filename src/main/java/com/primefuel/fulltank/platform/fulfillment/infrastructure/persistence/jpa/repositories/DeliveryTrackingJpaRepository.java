package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryTrackingPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryTrackingJpaRepository extends JpaRepository<DeliveryTrackingPersistenceEntity, Long> {
    boolean existsByEventId(String eventId);
    List<DeliveryTrackingPersistenceEntity> findByDeliveryIdOrderByRecordedAtAsc(Long deliveryId);
    Optional<DeliveryTrackingPersistenceEntity> findTopByDeliveryIdOrderByRecordedAtDesc(Long deliveryId);
}
