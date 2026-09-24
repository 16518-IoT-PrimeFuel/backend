package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record TrackingPointResource(@NotBlank String eventId,
                                    @NotNull Instant recordedAt,
                                    @NotNull Double latitude,
                                    @NotNull Double longitude,
                                    Double speedKph) {
}
