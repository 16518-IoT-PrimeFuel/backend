package com.primefuel.fulltank.platform.tracking.domain.repositories;

import com.primefuel.fulltank.platform.tracking.domain.model.entities.TransportEvidenceSample;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port of the raw transport evidence (S16/T16-A). Append-only: samples are written, never
 * edited. Listed by reception order so a consumer (T16-B query, T17 geofence) can rebuild the trail without
 * relying on the projection.
 */
public interface TransportEvidenceSampleRepository {

    TransportEvidenceSample save(TransportEvidenceSample sample);

    /** The delivery's samples in reception order (as they arrived at the platform). */
    List<TransportEvidenceSample> findByDeliveryId(Long deliveryId);

    /**
     * The delivery's samples ordered by the <em>device</em> clock ({@code recordedAt}) — the timeline a
     * tracker is read in — with reception order as the tie-breaker (T16-B). This is what makes a late
     * sample visible in its chronological place instead of at the end where it arrived.
     */
    List<TransportEvidenceSample> findByDeliveryIdOrderedByRecordedAt(Long deliveryId);

    long countByDeliveryId(Long deliveryId);

    long deleteByDeliveryId(Long deliveryId);

    /** The sample a client already sent under {@code clientEventId} for this delivery, if any. */
    Optional<TransportEvidenceSample> findByDeliveryIdAndClientEventId(Long deliveryId, String clientEventId);
}
