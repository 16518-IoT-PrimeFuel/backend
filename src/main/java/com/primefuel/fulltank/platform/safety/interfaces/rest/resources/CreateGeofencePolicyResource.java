package com.primefuel.fulltank.platform.safety.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

/**
 * Request body to create a new geofence-policy version for a delivery (S17). The tenant is resolved from the
 * principal and the delivery, so it is intentionally absent here.
 */
public record CreateGeofencePolicyResource(
        @NotNull Double centerLatitude,
        @NotNull Double centerLongitude,
        @NotNull Double radiusMeters) {
}
