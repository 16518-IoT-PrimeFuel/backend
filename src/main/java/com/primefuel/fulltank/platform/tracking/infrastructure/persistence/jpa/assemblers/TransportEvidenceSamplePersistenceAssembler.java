package com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.tracking.domain.model.entities.TransportEvidenceSample;
import com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.entities.TransportEvidenceSamplePersistenceEntity;

public final class TransportEvidenceSamplePersistenceAssembler {

    private TransportEvidenceSamplePersistenceAssembler() {
    }

    public static TransportEvidenceSample toDomain(TransportEvidenceSamplePersistenceEntity entity) {
        var sample = new TransportEvidenceSample();
        sample.setId(entity.getId());
        sample.setDeliveryId(entity.getDeliveryId());
        sample.setProviderId(entity.getProviderId());
        sample.setDriverId(entity.getDriverId());
        sample.setKind(entity.getKind());
        sample.setLatitude(entity.getLatitude());
        sample.setLongitude(entity.getLongitude());
        sample.setAccuracyMeters(entity.getAccuracyMeters());
        sample.setMilestone(entity.getMilestone());
        sample.setVolume(entity.getVolume());
        sample.setUnit(entity.getUnit());
        sample.setRecordedAt(entity.getRecordedAt());
        sample.setReceivedAt(entity.getReceivedAt());
        sample.setLatestAdvanced(entity.isLatestAdvanced());
        return sample;
    }

    public static TransportEvidenceSamplePersistenceEntity toPersistence(TransportEvidenceSample sample) {
        var entity = new TransportEvidenceSamplePersistenceEntity();
        if (sample.getId() != null) {
            entity.setId(sample.getId());
        }
        entity.setDeliveryId(sample.getDeliveryId());
        entity.setProviderId(sample.getProviderId());
        entity.setDriverId(sample.getDriverId());
        entity.setKind(sample.getKind());
        entity.setLatitude(sample.getLatitude());
        entity.setLongitude(sample.getLongitude());
        entity.setAccuracyMeters(sample.getAccuracyMeters());
        entity.setMilestone(sample.getMilestone());
        entity.setVolume(sample.getVolume());
        entity.setUnit(sample.getUnit());
        entity.setRecordedAt(sample.getRecordedAt());
        entity.setReceivedAt(sample.getReceivedAt());
        entity.setLatestAdvanced(sample.isLatestAdvanced());
        return entity;
    }
}
