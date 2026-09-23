package com.primefuel.fulltank.platform.safety.domain.model.valueobjects;

import java.time.Instant;

/**
 * The trusted position (and its provenance) that a geofence decision is evaluated against (S17). It is
 * deliberately a plain domain value — not the {@code tracking.api} snapshot — so the decision stays pure and
 * this domain never depends on another module's surface.
 *
 * @param evidenceId the raw tracking sample that produced the projection's latest position (the evidence
 *                   reference persisted with the decision)
 */
public record TrackedPosition(
        double latitude,
        double longitude,
        Double accuracyMeters,
        Instant recordedAt,
        Long evidenceId) {

    public TrackedPosition {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be within [-90, 90]");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be within [-180, 180]");
        }
        if (recordedAt == null) {
            throw new IllegalArgumentException("A position timestamp is required");
        }
    }
}
