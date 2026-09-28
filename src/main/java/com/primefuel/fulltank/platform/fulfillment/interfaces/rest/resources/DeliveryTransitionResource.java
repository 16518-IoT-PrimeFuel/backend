package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;

import java.time.Instant;

/** One journalled physical transition of a delivery. */
public record DeliveryTransitionResource(
        Long id,
        DeliveryPhysicalState fromState,
        DeliveryPhysicalState toState,
        long aggregateVersion,
        Instant occurredAt) {
}
