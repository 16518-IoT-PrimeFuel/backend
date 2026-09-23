package com.primefuel.fulltank.platform.tracking.domain.repositories;

import com.primefuel.fulltank.platform.tracking.domain.model.entities.TransportEvidenceSample;

import java.util.List;

/**
 * Persistence port of the raw transport evidence (S16/T16-A). Append-only: samples are written, never
 * edited. Listed by reception order so a consumer (T16-B query, T17 geofence) can rebuild the trail without
 * relying on the projection.
 */
public interface TransportEvidenceSampleRepository {

    TransportEvidenceSample save(TransportEvidenceSample sample);

    List<TransportEvidenceSample> findByDeliveryId(Long deliveryId);

    long countByDeliveryId(Long deliveryId);
}
