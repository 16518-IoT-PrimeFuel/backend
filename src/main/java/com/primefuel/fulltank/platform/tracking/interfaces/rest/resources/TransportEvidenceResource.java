package com.primefuel.fulltank.platform.tracking.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Transport-evidence request (S16/T16-A). The body carries <em>either</em> a position sample
 * ({@code type=POSITION}: {@code latitude}, {@code longitude}, {@code recordedAt}, optional
 * {@code accuracyMeters}) <em>or</em> a discrete load milestone ({@code type=LOAD}: {@code milestone}
 * LOADED/UNLOADED, optional {@code volume}/{@code unit}).
 *
 * <p>There is deliberately no {@code driverId} field: the assigned driver is resolved server-side from the
 * delivery and the principal, never trusted from the request.
 *
 * <p>{@code eventId} is an optional client idempotency key, unique per delivery: a retry with the same key
 * returns the original acknowledgement (200) without storing a new sample.
 */
public record TransportEvidenceResource(
        @NotBlank String type,
        Double latitude,
        Double longitude,
        Double accuracyMeters,
        String milestone,
        Double volume,
        String unit,
        Instant recordedAt,
        @Size(max = 160) String eventId) {
}
