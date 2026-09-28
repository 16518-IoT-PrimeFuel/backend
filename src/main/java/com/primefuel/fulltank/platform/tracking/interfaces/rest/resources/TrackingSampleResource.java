package com.primefuel.fulltank.platform.tracking.interfaces.rest.resources;

import java.time.Instant;

/**
 * One raw transport-evidence sample in a delivery's timeline (S16/T16-B). Ordered by the device clock
 * ({@code recordedAt}); {@code receivedAt} and {@code latestAdvanced} stay visible so a consumer can tell a
 * late sample (arrived out of order) from one that actually moved the projection's latest value.
 */
public record TrackingSampleResource(
        Long evidenceId,
        String kind,
        Double latitude,
        Double longitude,
        Double accuracyMeters,
        String milestone,
        Double volume,
        String unit,
        Instant recordedAt,
        Instant receivedAt,
        boolean latestAdvanced) {
}
