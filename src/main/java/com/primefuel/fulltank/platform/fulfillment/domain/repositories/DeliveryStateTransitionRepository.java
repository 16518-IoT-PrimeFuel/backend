package com.primefuel.fulltank.platform.fulfillment.domain.repositories;

import com.primefuel.fulltank.platform.fulfillment.domain.model.entities.DeliveryStateTransition;

import java.util.List;

/** Append-only journal of the delivery physical transitions; rows are never updated or deleted. */
public interface DeliveryStateTransitionRepository {

    DeliveryStateTransition add(DeliveryStateTransition transition);

    List<DeliveryStateTransition> findByDeliveryId(Long deliveryId);
}
