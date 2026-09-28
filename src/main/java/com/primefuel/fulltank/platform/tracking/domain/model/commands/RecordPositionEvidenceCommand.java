package com.primefuel.fulltank.platform.tracking.domain.model.commands;

import java.time.Instant;

/**
 * A position sample to record for a delivery (S16/T16-A). {@code providerId} and {@code driverId} are
 * resolved server-side from the delivery assignment by the REST adapter — never trusted from a request body.
 */
public record RecordPositionEvidenceCommand(
        Long deliveryId,
        Long providerId,
        Long driverId,
        Double latitude,
        Double longitude,
        Double accuracyMeters,
        Instant recordedAt,
        String eventId) {
}
