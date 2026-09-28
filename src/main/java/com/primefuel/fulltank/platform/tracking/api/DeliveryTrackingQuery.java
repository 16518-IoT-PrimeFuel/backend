package com.primefuel.fulltank.platform.tracking.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Public read seam of the tracking module (S16/T16-B). It is the only way to consult transport tracking:
 * nothing outside {@code tracking} may read the projection or the raw samples.
 *
 * <p>Two readings are offered, matching the two consumers of S16:
 * <ul>
 *   <li>{@link #latest} — the current <em>trusted</em> position and load state of a delivery (the projection
 *       that T17's geofence and any tracker UI consume);</li>
 *   <li>{@link #samples} — the chronological trail, ordered by the device clock ({@code recordedAt}) with
 *       {@code receivedAt}/{@code latestAdvanced} exposed, so a consumer can tell a late sample (arrived out
 *       of order) from one that actually moved the latest.</li>
 * </ul>
 *
 * <p>Tenancy is enforced by the caller (the REST adapter, as in T16-A): this seam answers for whatever
 * delivery id it is given, and the controller resolves whether the principal may see it.
 */
public interface DeliveryTrackingQuery {

    /** The latest trusted projection of a delivery, or empty when nothing has been recorded yet. */
    Optional<TrackingSnapshot> latest(Long deliveryId);

    /** The delivery's chronological trail; empty when nothing has been recorded yet. */
    List<TrackingSampleSnapshot> samples(Long deliveryId);

    record TrackingSnapshot(
            Long deliveryId,
            Long providerId,
            Long driverId,
            Double lastLatitude,
            Double lastLongitude,
            Double lastAccuracyMeters,
            Instant lastPositionAt,
            Long lastPositionEvidenceId,
            boolean loaded,
            String lastLoadMilestone,
            Instant lastLoadAt,
            Double lastLoadVolume,
            String lastLoadUnit,
            Long lastLoadEvidenceId,
            int version) {
    }

    record TrackingSampleSnapshot(
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
}
