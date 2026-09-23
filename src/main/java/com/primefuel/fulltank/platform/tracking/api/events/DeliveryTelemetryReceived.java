package com.primefuel.fulltank.platform.tracking.api.events;

import java.time.Instant;

/**
 * A driver-app position sample was received for a delivery (S16). Event type {@code
 * delivery.telemetry.received.v1}.
 *
 * <p>The name and shape are kept from the original S16 contract (now published by the REST command instead
 * of a telemetry consumer). {@code latestAdvanced} distinguishes a sample that moved the projection's latest
 * position from a late one that is only preserved as raw evidence — a late sample still emits the event
 * (it <em>was</em> received), it just does not regress the projection.
 */
public record DeliveryTelemetryReceived(
        Long evidenceId,
        Long deliveryId,
        Long providerId,
        Long driverId,
        double latitude,
        double longitude,
        Double accuracyMeters,
        Instant recordedAt,
        boolean latestAdvanced,
        Instant occurredAt) {

    public String toPayloadJson() {
        return "{\"evidenceId\":%s,\"deliveryId\":%s,\"providerId\":%s,\"driverId\":%s,\"latitude\":%s,"
                .formatted(TransportEventJson.number(evidenceId), TransportEventJson.number(deliveryId),
                        TransportEventJson.number(providerId), TransportEventJson.number(driverId),
                        TransportEventJson.number(latitude))
                + "\"longitude\":%s,\"accuracyMeters\":%s,\"recordedAt\":%s,\"latestAdvanced\":%s,"
                .formatted(TransportEventJson.number(longitude), TransportEventJson.number(accuracyMeters),
                        TransportEventJson.string(recordedAt), latestAdvanced)
                + "\"occurredAt\":%s}".formatted(TransportEventJson.string(occurredAt));
    }
}
