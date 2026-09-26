package com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence;

import org.springframework.data.repository.Repository;
import java.util.List;

/** Append-only incident persistence: deliberately exposes no update or delete operation. */
public interface SafetyIncidentRepository extends Repository<SafetyIncidentEntity, Long> {
    SafetyIncidentEntity save(SafetyIncidentEntity incident);
    List<SafetyIncidentEntity> findByDeliveryIdOrderByOccurredAtAsc(Long deliveryId);
}
