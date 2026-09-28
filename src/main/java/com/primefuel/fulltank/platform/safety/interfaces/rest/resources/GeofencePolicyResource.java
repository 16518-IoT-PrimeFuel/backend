package com.primefuel.fulltank.platform.safety.interfaces.rest.resources;

/** A geofence-policy version (S17). {@code policyVersion} increases on every replacement. */
public record GeofencePolicyResource(
        Long id,
        Long deliveryId,
        Long providerId,
        double centerLatitude,
        double centerLongitude,
        double radiusMeters,
        int policyVersion) {
}
