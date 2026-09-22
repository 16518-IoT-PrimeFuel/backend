package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.fulfillment.domain.model.entities.DeliveryStateTransition;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryStateTransitionPersistenceEntity;

public final class DeliveryStateTransitionAssembler {

    private DeliveryStateTransitionAssembler() {
    }

    public static DeliveryStateTransition toDomain(DeliveryStateTransitionPersistenceEntity entity) {
        if (entity == null) return null;
        return new DeliveryStateTransition(entity.getId(), entity.getDeliveryId(), entity.getFromState(),
                entity.getToState(), entity.getAggregateVersion(), entity.getOccurredAt());
    }

    public static DeliveryStateTransitionPersistenceEntity toPersistence(DeliveryStateTransition domain) {
        if (domain == null) return null;
        var entity = new DeliveryStateTransitionPersistenceEntity();
        if (domain.id() != null) {
            entity.setId(domain.id());
        }
        entity.setDeliveryId(domain.deliveryId());
        entity.setFromState(domain.fromState());
        entity.setToState(domain.toState());
        entity.setAggregateVersion(domain.aggregateVersion());
        entity.setOccurredAt(domain.occurredAt());
        return entity;
    }
}
