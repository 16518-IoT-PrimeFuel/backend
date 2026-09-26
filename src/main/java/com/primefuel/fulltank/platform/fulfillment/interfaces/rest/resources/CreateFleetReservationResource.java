package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import java.time.Instant;

public record CreateFleetReservationResource(String idempotencyKey, Long providerId, Long deliveryId,
                                             Long driverId, Long vehicleId, Instant windowStart,
                                             Instant windowEnd, Double volume) {
}
