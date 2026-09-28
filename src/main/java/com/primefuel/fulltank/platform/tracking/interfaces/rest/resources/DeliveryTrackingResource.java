package com.primefuel.fulltank.platform.tracking.interfaces.rest.resources;

import java.time.Instant;

/**
 * The latest trusted tracking projection of a delivery (S16/T16-B): the current position and load state, plus
 * the ids of the raw evidence rows that produced them. This is the "where is it now / is it loaded" view.
 */
public record DeliveryTrackingResource(
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
