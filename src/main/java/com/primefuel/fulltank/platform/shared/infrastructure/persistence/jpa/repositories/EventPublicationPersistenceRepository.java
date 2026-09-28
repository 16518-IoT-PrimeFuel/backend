package com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.EventPublicationPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventPublicationPersistenceRepository extends JpaRepository<EventPublicationPersistenceEntity, Long> {

    Optional<EventPublicationPersistenceEntity> findByEventId(String eventId);

    List<EventPublicationPersistenceEntity> findByAggregateTypeAndAggregateIdOrderByIdAsc(String aggregateType, String aggregateId);

    List<EventPublicationPersistenceEntity> findByCompletedAtIsNullOrderByIdAsc();
}
