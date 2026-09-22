package com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.ConsumedEventPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsumedEventPersistenceRepository extends JpaRepository<ConsumedEventPersistenceEntity, Long> {

    boolean existsByConsumerAndEventId(String consumer, String eventId);
}
