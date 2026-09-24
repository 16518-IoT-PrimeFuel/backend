package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TelemetryCheckpointPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TelemetryCheckpointJpaRepository extends JpaRepository<TelemetryCheckpointPersistenceEntity, Long> {
    Optional<TelemetryCheckpointPersistenceEntity> findByDeviceIdAndChannel(String deviceId, String channel);

    @Modifying
    @Query("update TelemetryCheckpointPersistenceEntity c set c.lastSequenceNumber = :sequence "
            + "where c.deviceId = :deviceId and c.channel = :channel and c.lastSequenceNumber < :sequence")
    int advance(@Param("deviceId") String deviceId, @Param("channel") String channel,
                @Param("sequence") long sequence);
}
