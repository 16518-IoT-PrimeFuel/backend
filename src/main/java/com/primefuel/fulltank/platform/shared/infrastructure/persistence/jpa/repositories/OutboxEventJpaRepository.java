package com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.OutboxEventPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventPersistenceEntity, Long> {
    boolean existsByEventKey(String eventKey);
}
