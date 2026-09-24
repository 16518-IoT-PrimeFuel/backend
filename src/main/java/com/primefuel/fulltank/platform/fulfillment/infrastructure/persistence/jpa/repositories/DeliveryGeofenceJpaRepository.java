package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryGeofencePersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryGeofenceJpaRepository extends JpaRepository<DeliveryGeofencePersistenceEntity, Long> {
    Optional<DeliveryGeofencePersistenceEntity> findTopByDeliveryIdAndStatusOrderByVersionDesc(Long deliveryId, String status);
}
