package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TelemetryReadingPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryReadingJpaRepository extends JpaRepository<TelemetryReadingPersistenceEntity, Long> {
    boolean existsByEventId(String eventId);

    boolean existsByDeviceIdAndChannelAndSequenceNumber(String deviceId, String channel, long sequenceNumber);
}
