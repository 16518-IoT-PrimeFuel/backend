package com.primefuel.fulltank.platform.fulfillment.domain.model.entities;

import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;

import java.time.Instant;

/**
 * One append-only row of the delivery physical journal (S14/T14-A). It records what actually happened, so
 * the history is observed rather than reconstructed. {@code fromState} is null only when a legacy row is
 * first materialized into {@code ASSIGNED}, since it had no stored physical state before.
 */
public record DeliveryStateTransition(
        Long id,
        Long deliveryId,
        DeliveryPhysicalState fromState,
        DeliveryPhysicalState toState,
        long aggregateVersion,
        Instant occurredAt) {
}
