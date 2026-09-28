package com.primefuel.fulltank.platform.telemetry.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import com.primefuel.fulltank.platform.telemetry.domain.model.aggregates.TelemetryReading;
import com.primefuel.fulltank.platform.telemetry.infrastructure.persistence.jpa.entities.TelemetryReadingPersistenceEntity;

public final class TelemetryReadingPersistenceAssembler {

    private TelemetryReadingPersistenceAssembler() {
    }

    public static TelemetryReading toDomainFromPersistence(TelemetryReadingPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new TelemetryReading();
        domain.setId(entity.getId());
        domain.setSchemaVersion(entity.getSchemaVersion());
        domain.setDeviceId(entity.getDeviceId());
        domain.setChannel(entity.getChannel());
        domain.setSequence(entity.getSequence());
        domain.setCapturedAt(entity.getCapturedAt());
        domain.setReceivedAt(entity.getReceivedAt());
        domain.setTankId(entity.getTankId());
        domain.setOrganizationId(entity.getOrganizationId());
        domain.setLevel(new Volume(entity.getLevelAmount(), Unit.valueOf(entity.getLevelUnit())));
        domain.setQuality(entity.getQuality());
        domain.setQuarantineReason(entity.getQuarantineReason());
        return domain;
    }

    public static TelemetryReadingPersistenceEntity toPersistenceFromDomain(TelemetryReading domain) {
        if (domain == null) return null;
        var entity = new TelemetryReadingPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setSchemaVersion(domain.getSchemaVersion());
        entity.setDeviceId(domain.getDeviceId());
        entity.setChannel(domain.getChannel());
        entity.setSequence(domain.getSequence());
        entity.setCapturedAt(domain.getCapturedAt());
        entity.setReceivedAt(domain.getReceivedAt());
        entity.setTankId(domain.getTankId());
        entity.setOrganizationId(domain.getOrganizationId());
        entity.setLevelAmount(domain.getLevel().amount());
        entity.setLevelUnit(domain.getLevel().unit().name());
        entity.setQuality(domain.getQuality());
        entity.setQuarantineReason(domain.getQuarantineReason());
        return entity;
    }
}
