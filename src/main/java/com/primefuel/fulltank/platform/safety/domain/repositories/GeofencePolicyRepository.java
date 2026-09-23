package com.primefuel.fulltank.platform.safety.domain.repositories;

import com.primefuel.fulltank.platform.safety.domain.model.aggregates.GeofencePolicy;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port of geofence policies (S17). Append-only: a new version is a new row, the previous one is
 * kept. There is no update and no delete.
 */
public interface GeofencePolicyRepository {

    GeofencePolicy save(GeofencePolicy policy);

    /** The highest-version policy for a delivery (the one in force), if any. */
    Optional<GeofencePolicy> findLatestByDeliveryId(Long deliveryId);

    /** All versions for a delivery, newest first. */
    List<GeofencePolicy> findByDeliveryId(Long deliveryId);
}
