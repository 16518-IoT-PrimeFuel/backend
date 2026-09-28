package com.primefuel.fulltank.platform.safety.domain.model.commands;

/**
 * Creates a new, immutable version of the geofence policy for a delivery (S17). {@code providerId} is
 * resolved server-side from the delivery's tenant by the REST adapter — never taken from the request body.
 */
public record CreateGeofencePolicyCommand(
        Long deliveryId,
        Long providerId,
        Double centerLatitude,
        Double centerLongitude,
        Double radiusMeters) {
}
