package com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.tracking.domain.model.aggregates.DeliveryTracking;
import com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.entities.DeliveryTrackingPersistenceEntity;

public final class DeliveryTrackingPersistenceAssembler {

    private DeliveryTrackingPersistenceAssembler() {
    }

    public static DeliveryTracking toDomain(DeliveryTrackingPersistenceEntity entity) {
        var tracking = new DeliveryTracking(entity.getDeliveryId(), entity.getProviderId(), entity.getDriverId());
        tracking.setId(entity.getId());
        tracking.setLastLatitude(entity.getLastLatitude());
        tracking.setLastLongitude(entity.getLastLongitude());
        tracking.setLastAccuracyMeters(entity.getLastAccuracyMeters());
        tracking.setLastPositionAt(entity.getLastPositionAt());
        tracking.setLastPositionEvidenceId(entity.getLastPositionEvidenceId());
        tracking.setLoaded(entity.isLoaded());
        tracking.setLastLoadMilestone(entity.getLastLoadMilestone());
        tracking.setLastLoadAt(entity.getLastLoadAt());
        tracking.setLastLoadVolume(entity.getLastLoadVolume());
        tracking.setLastLoadUnit(entity.getLastLoadUnit());
        tracking.setLastLoadEvidenceId(entity.getLastLoadEvidenceId());
        tracking.setVersion(entity.getVersion());
        return tracking;
    }

    public static DeliveryTrackingPersistenceEntity toPersistence(DeliveryTracking tracking) {
        var entity = new DeliveryTrackingPersistenceEntity();
        if (tracking.getId() != null) {
            entity.setId(tracking.getId());
        }
        entity.setDeliveryId(tracking.getDeliveryId());
        entity.setProviderId(tracking.getProviderId());
        entity.setDriverId(tracking.getDriverId());
        entity.setLastLatitude(tracking.getLastLatitude());
        entity.setLastLongitude(tracking.getLastLongitude());
        entity.setLastAccuracyMeters(tracking.getLastAccuracyMeters());
        entity.setLastPositionAt(tracking.getLastPositionAt());
        entity.setLastPositionEvidenceId(tracking.getLastPositionEvidenceId());
        entity.setLoaded(tracking.isLoaded());
        entity.setLastLoadMilestone(tracking.getLastLoadMilestone());
        entity.setLastLoadAt(tracking.getLastLoadAt());
        entity.setLastLoadVolume(tracking.getLastLoadVolume());
        entity.setLastLoadUnit(tracking.getLastLoadUnit());
        entity.setLastLoadEvidenceId(tracking.getLastLoadEvidenceId());
        entity.setVersion(tracking.getVersion());
        return entity;
    }
}
