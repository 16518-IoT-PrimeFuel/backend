package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.application.ports.TelemetryInboxStore;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TelemetryCheckpointPersistenceEntity;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TelemetryInboxPersistenceEntity;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.TelemetryCheckpointJpaRepository;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.TelemetryInboxJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TelemetryInboxStoreAdapter implements TelemetryInboxStore {
    private final TelemetryInboxJpaRepository inbox;
    private final TelemetryCheckpointJpaRepository checkpoints;

    public TelemetryInboxStoreAdapter(TelemetryInboxJpaRepository inbox, TelemetryCheckpointJpaRepository checkpoints) {
        this.inbox = inbox;
        this.checkpoints = checkpoints;
    }

    @Override
    public boolean claim(String eventId, String deviceId, String channel, long sequenceNumber, Instant receivedAt) {
        var existing = inbox.findByEventId(eventId);
        if (existing.isPresent()) {
            var entity = existing.get();
            if ("ACCEPTED".equals(entity.getStatus())) return false;
            entity.setStatus("PROCESSING");
            entity.setAttempts(entity.getAttempts() + 1);
            entity.setLastError(null);
            inbox.save(entity);
            return true;
        }
        var entity = new TelemetryInboxPersistenceEntity();
        entity.setEventId(eventId); entity.setDeviceId(deviceId); entity.setChannel(channel);
        entity.setSequenceNumber(sequenceNumber); entity.setStatus("PROCESSING"); entity.setAttempts(1);
        entity.setReceivedAt(receivedAt);
        try {
            inbox.save(entity);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    @Override
    public void complete(String eventId, String status, String error) {
        inbox.findByEventId(eventId).ifPresent(entity -> {
            entity.setStatus(status);
            entity.setLastError(error);
            inbox.save(entity);
        });
    }

    @Override
    public void advanceCheckpoint(String deviceId, String channel, long sequenceNumber) {
        if (checkpoints.advance(deviceId, channel, sequenceNumber) > 0) return;
        if (checkpoints.findByDeviceIdAndChannel(deviceId, channel).isEmpty()) {
            var entity = new TelemetryCheckpointPersistenceEntity();
            entity.setDeviceId(deviceId); entity.setChannel(channel); entity.setLastSequenceNumber(sequenceNumber);
            checkpoints.save(entity);
        }
    }
}
