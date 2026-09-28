package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryBusinessJournalEntity;
import org.springframework.data.repository.Repository;
import java.util.List;

/** Append-only business facts; no update or delete operation is exposed. */
public interface DeliveryBusinessJournalRepository extends Repository<DeliveryBusinessJournalEntity, Long> {
    DeliveryBusinessJournalEntity save(DeliveryBusinessJournalEntity entry);
    List<DeliveryBusinessJournalEntity> findByDeliveryIdOrderByOccurredAtAsc(Long deliveryId);
}
