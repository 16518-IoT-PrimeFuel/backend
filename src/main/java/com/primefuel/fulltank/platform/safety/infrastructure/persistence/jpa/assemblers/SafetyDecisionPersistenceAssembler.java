package com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.safety.domain.model.entities.SafetyDecision;
import com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.entities.SafetyDecisionPersistenceEntity;

public final class SafetyDecisionPersistenceAssembler {

    private SafetyDecisionPersistenceAssembler() {
    }

    public static SafetyDecision toDomain(SafetyDecisionPersistenceEntity entity) {
        var decision = new SafetyDecision();
        decision.setId(entity.getId());
        decision.setDeliveryId(entity.getDeliveryId());
        decision.setProviderId(entity.getProviderId());
        decision.setPolicyId(entity.getPolicyId());
        decision.setPolicyVersion(entity.getPolicyVersion());
        decision.setAuthorized(entity.isAuthorized());
        decision.setReason(entity.getReason());
        decision.setTrackingEvidenceId(entity.getTrackingEvidenceId());
        decision.setObservedAt(entity.getObservedAt());
        decision.setEvaluatedAt(entity.getEvaluatedAt());
        decision.setDistanceMeters(entity.getDistanceMeters());
        decision.setAccuracyMeters(entity.getAccuracyMeters());
        return decision;
    }

    public static SafetyDecisionPersistenceEntity toPersistence(SafetyDecision decision) {
        var entity = new SafetyDecisionPersistenceEntity();
        if (decision.getId() != null) {
            entity.setId(decision.getId());
        }
        entity.setDeliveryId(decision.getDeliveryId());
        entity.setProviderId(decision.getProviderId());
        entity.setPolicyId(decision.getPolicyId());
        entity.setPolicyVersion(decision.getPolicyVersion());
        entity.setAuthorized(decision.isAuthorized());
        entity.setReason(decision.getReason());
        entity.setTrackingEvidenceId(decision.getTrackingEvidenceId());
        entity.setObservedAt(decision.getObservedAt());
        entity.setEvaluatedAt(decision.getEvaluatedAt());
        entity.setDistanceMeters(decision.getDistanceMeters());
        entity.setAccuracyMeters(decision.getAccuracyMeters());
        return entity;
    }
}
