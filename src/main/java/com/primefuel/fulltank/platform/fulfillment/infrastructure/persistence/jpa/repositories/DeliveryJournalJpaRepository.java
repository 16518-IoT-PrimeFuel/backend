package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryJournalPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryJournalJpaRepository extends JpaRepository<DeliveryJournalPersistenceEntity, Long> {
    boolean existsByEventId(String eventId);
    List<DeliveryJournalPersistenceEntity> findByDeliveryIdOrderByOccurredAtAsc(Long deliveryId);
}
