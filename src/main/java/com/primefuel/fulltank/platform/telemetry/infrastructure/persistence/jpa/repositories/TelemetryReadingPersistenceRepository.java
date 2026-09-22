package com.primefuel.fulltank.platform.telemetry.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.telemetry.domain.model.valueobjects.ReadingQuality;
import com.primefuel.fulltank.platform.telemetry.infrastructure.persistence.jpa.entities.TelemetryReadingPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelemetryReadingPersistenceRepository
        extends JpaRepository<TelemetryReadingPersistenceEntity, Long> {

    Optional<TelemetryReadingPersistenceEntity> findByDeviceIdAndChannelAndSequence(
            String deviceId, String channel, long sequence);

    List<TelemetryReadingPersistenceEntity> findByDeviceIdAndChannelOrderByCapturedAtAsc(
            String deviceId, String channel);

    long countByQuality(ReadingQuality quality);
}
