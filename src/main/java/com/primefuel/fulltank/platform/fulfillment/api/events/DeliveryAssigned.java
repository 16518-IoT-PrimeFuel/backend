package com.primefuel.fulltank.platform.fulfillment.api.events;

import java.time.Instant;

/** A delivery entered the physical machine ({@code → ASSIGNED}). Event type {@code delivery.assigned.v1}. */
public record DeliveryAssigned(
        Long deliveryId,
        Long orderId,
        Long providerId,
        String physicalState,
        Instant occurredAt) {

    public String toPayloadJson() {
        return "{\"deliveryId\":%s,\"orderId\":%s,\"providerId\":%s,\"physicalState\":%s,\"occurredAt\":%s}"
                .formatted(DeliveryEventJson.number(deliveryId), DeliveryEventJson.number(orderId),
                        DeliveryEventJson.number(providerId), DeliveryEventJson.string(physicalState),
                        DeliveryEventJson.string(occurredAt));
    }
}
