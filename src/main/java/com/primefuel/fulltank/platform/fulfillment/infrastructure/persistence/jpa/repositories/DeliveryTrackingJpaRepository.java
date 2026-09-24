package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryTrackingPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryTrackingJpaRepository extends JpaRepository<DeliveryTrackingPersistenceEntity, Long> {
    boolean existsByEventId(String eventId);
}
