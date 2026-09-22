package com.primefuel.fulltank.platform.fulfillment.api.events;

import java.time.Instant;

/**
 * The delivery reached a terminal non-completed state. Event type {@code delivery.failed.v1}.
 * {@code terminalState} distinguishes an operational failure ({@code FAILED}) from an explicit
 * {@code CANCELLED} — the roadmap defines a single failed event, so cancellation is reported through it.
 */
public record DeliveryFailed(
        Long deliveryId,
        Long orderId,
        Long providerId,
        String terminalState,
        String reason,
        Instant occurredAt) {

    public String toPayloadJson() {
        return ("{\"deliveryId\":%s,\"orderId\":%s,\"providerId\":%s,\"terminalState\":%s,\"reason\":%s,"
                + "\"occurredAt\":%s}")
                .formatted(DeliveryEventJson.number(deliveryId), DeliveryEventJson.number(orderId),
                        DeliveryEventJson.number(providerId), DeliveryEventJson.string(terminalState),
                        DeliveryEventJson.string(reason), DeliveryEventJson.string(occurredAt));
    }
}
