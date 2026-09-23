package com.primefuel.fulltank.platform.tracking.api;

import java.util.Optional;

/**
 * Maintenance seam of the tracking projection (S16/T16-B). The projection is a derived value: it is
 * <em>rebuildable</em> from the raw, append-only evidence. This seam exposes that reconstruction so a
 * corrupted, missing or drifted projection can be recomputed deterministically.
 *
 * <p>{@link #rebuild} replays the delivery's samples in device-clock ({@code recordedAt}) order — from a
 * reset state, through the same {@code DeliveryTracking} aggregate the live recorder uses — so processing
 * the <em>same evidence</em> always yields the <em>same</em> projection, regardless of the order in which the
 * samples happened to arrive (jitter). It is not exposed over REST: it is a test-provable determinism
 * guarantee, not an operator command (T16-B).
 */
public interface TrackingProjectionRebuilder {

    /**
     * Recomputes and persists the projection for {@code deliveryId} from its raw samples. Empty when the
     * delivery has no samples (nothing to rebuild). The returned snapshot is the rebuilt projection.
     */
    Optional<DeliveryTrackingQuery.TrackingSnapshot> rebuild(Long deliveryId);
}
