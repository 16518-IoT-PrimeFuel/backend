package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

public record GeofenceEvaluationResource(@NotNull Double latitude,
                                         @NotNull Double longitude) {
}
