package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TelemetryInboxPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelemetryInboxJpaRepository extends JpaRepository<TelemetryInboxPersistenceEntity, Long> {
    Optional<TelemetryInboxPersistenceEntity> findByEventId(String eventId);
}
