package com.primefuel.fulltank.platform.fulfillment.api.events;

import java.time.Instant;

/** Physical execution started. Event type {@code delivery.started.v1}. */
public record DeliveryStarted(
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
