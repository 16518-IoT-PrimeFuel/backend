package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ValveCommandResource(@NotBlank String commandId,
                                   @NotBlank String desiredState,
                                   @NotNull Double latitude,
                                   @NotNull Double longitude,
                                   @NotNull Instant capturedAt,
                                   @NotNull Double accuracyMeters) {
}
