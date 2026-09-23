package com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.safety.domain.model.aggregates.GeofencePolicy;
import com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.entities.GeofencePolicyPersistenceEntity;

public final class GeofencePolicyPersistenceAssembler {

    private GeofencePolicyPersistenceAssembler() {
    }

    public static GeofencePolicy toDomain(GeofencePolicyPersistenceEntity entity) {
        var policy = new GeofencePolicy();
        policy.setId(entity.getId());
        policy.setDeliveryId(entity.getDeliveryId());
        policy.setProviderId(entity.getProviderId());
        policy.setCenterLatitude(entity.getCenterLatitude());
        policy.setCenterLongitude(entity.getCenterLongitude());
        policy.setRadiusMeters(entity.getRadiusMeters());
        policy.setPolicyVersion(entity.getPolicyVersion());
        return policy;
    }

    public static GeofencePolicyPersistenceEntity toPersistence(GeofencePolicy policy) {
        var entity = new GeofencePolicyPersistenceEntity();
        if (policy.getId() != null) {
            entity.setId(policy.getId());
        }
        entity.setDeliveryId(policy.getDeliveryId());
        entity.setProviderId(policy.getProviderId());
        entity.setCenterLatitude(policy.getCenterLatitude());
        entity.setCenterLongitude(policy.getCenterLongitude());
        entity.setRadiusMeters(policy.getRadiusMeters());
        entity.setPolicyVersion(policy.getPolicyVersion());
        return entity;
    }
}
