package com.primefuel.fulltank.platform.fulfillment.application.ports;

public interface GeofenceStore {
    GeofenceData upsert(Long deliveryId, Double latitude, Double longitude, Double radiusMeters);

    GeofenceData active(Long deliveryId);

    record GeofenceData(Long deliveryId, Integer version, Double latitude, Double longitude, Double radiusMeters) {
    }
}
