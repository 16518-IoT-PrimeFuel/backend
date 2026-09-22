package com.primefuel.fulltank.platform.fulfillment.api.events;

import java.time.Instant;

/** The vehicle arrived at the delivery site. Event type {@code delivery.arrived.v1}. */
public record DeliveryArrived(
        Long deliveryId,
        Long orderId,
        Long providerId,
        String previousState,
        Instant occurredAt) {

    public String toPayloadJson() {
        return "{\"deliveryId\":%s,\"orderId\":%s,\"providerId\":%s,\"previousState\":%s,\"occurredAt\":%s}"
                .formatted(DeliveryEventJson.number(deliveryId), DeliveryEventJson.number(orderId),
                        DeliveryEventJson.number(providerId), DeliveryEventJson.string(previousState),
                        DeliveryEventJson.string(occurredAt));
    }
}
