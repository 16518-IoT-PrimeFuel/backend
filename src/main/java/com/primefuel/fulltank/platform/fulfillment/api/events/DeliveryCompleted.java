package com.primefuel.fulltank.platform.fulfillment.api.events;

import java.time.Instant;

/**
 * The physical delivery closed with its evidence: the delivered volume (U11). The requested volume is
 * carried alongside and is never overwritten. Event type {@code delivery.completed.v1}.
 */
public record DeliveryCompleted(
        Long deliveryId,
        Long orderId,
        Long providerId,
        Double deliveredVolume,
        Double requestedVolume,
        Instant occurredAt) {

    public String toPayloadJson() {
        return ("{\"deliveryId\":%s,\"orderId\":%s,\"providerId\":%s,\"deliveredVolume\":%s,"
                + "\"requestedVolume\":%s,\"occurredAt\":%s}")
                .formatted(DeliveryEventJson.number(deliveryId), DeliveryEventJson.number(orderId),
                        DeliveryEventJson.number(providerId), DeliveryEventJson.number(deliveredVolume),
                        DeliveryEventJson.number(requestedVolume), DeliveryEventJson.string(occurredAt));
    }
}
