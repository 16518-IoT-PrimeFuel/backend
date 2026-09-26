package com.primefuel.fulltank.platform.fulfillment.application.ports;

import java.time.Instant;

public record TrackingPointData(String eventId, Instant recordedAt, Double latitude,
                               Double longitude, Double speedKph) {
}
