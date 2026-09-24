package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GeofenceResource(@NotNull Double latitude,
                               @NotNull Double longitude,
                               @NotNull @Positive Double radiusMeters) {
}
