package com.primefuel.fulltank.platform.tracking.domain.repositories;

import com.primefuel.fulltank.platform.tracking.domain.model.aggregates.DeliveryTracking;

import java.util.Optional;

/** Persistence port of the tracking projection (S16/T16-A). */
public interface DeliveryTrackingRepository {

    Optional<DeliveryTracking> findByDeliveryId(Long deliveryId);

    DeliveryTracking save(DeliveryTracking tracking);
}
