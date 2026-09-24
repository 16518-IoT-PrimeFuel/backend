package com.primefuel.fulltank.platform.fulfillment.application.ports;

import java.time.Instant;

public interface TrackingStore {
    boolean append(String eventId, Long deliveryId, Instant recordedAt,
                   Double latitude, Double longitude, Double speedKph);
}
