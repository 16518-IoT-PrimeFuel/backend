package com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.OutboxEventPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventPersistenceEntity, Long> {
    boolean existsByEventKey(String eventKey);
    List<OutboxEventPersistenceEntity> findTop50ByStatusOrderByOccurredAtAsc(String status);
}
