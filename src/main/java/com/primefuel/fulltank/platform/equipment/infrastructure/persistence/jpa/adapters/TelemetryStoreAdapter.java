package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.application.ports.TelemetryReadingData;
import com.primefuel.fulltank.platform.equipment.application.ports.TelemetryStore;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TelemetryReadingPersistenceEntity;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.TelemetryReadingJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class TelemetryStoreAdapter implements TelemetryStore {
    private final TelemetryReadingJpaRepository repository;

    public TelemetryStoreAdapter(TelemetryReadingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean saveIfAbsent(TelemetryReadingData reading) {
        if (repository.existsByEventId(reading.eventId())
                || repository.existsByDeviceIdAndChannelAndSequenceNumber(reading.deviceId(), reading.channel(), reading.sequenceNumber())) {
            return false;
        }
        var entity = new TelemetryReadingPersistenceEntity();
        entity.setEventId(reading.eventId());
        entity.setDeviceId(reading.deviceId());
        entity.setChannel(reading.channel());
        entity.setSequenceNumber(reading.sequenceNumber());
        entity.setTankId(reading.tankId());
        entity.setSchemaVersion(reading.schemaVersion());
        entity.setCapturedAt(reading.capturedAt());
        entity.setReceivedAt(reading.receivedAt());
        entity.setLevelValue(reading.levelValue());
        entity.setUnit(reading.unit());
        entity.setQuality(reading.quality());
        try {
            repository.save(entity);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }
}
