package com.primefuel.fulltank.platform.fulfillment.application.ports;

import java.time.Instant;

public record FleetReservation(String idempotencyKey, Long providerId, Long deliveryId,
                               Long driverId, Long vehicleId, Instant windowStart,
                               Instant windowEnd, Double volume) {
}
