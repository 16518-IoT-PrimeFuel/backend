package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ValveCommandResource(@NotBlank String commandId,
                                   @NotBlank String desiredState,
                                   @NotNull Double latitude,
                                   @NotNull Double longitude) {
}
