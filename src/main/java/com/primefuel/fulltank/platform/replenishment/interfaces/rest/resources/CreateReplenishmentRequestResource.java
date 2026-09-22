package com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReplenishmentRequestResource(
        @NotNull Long customerAccountId,
        Long tankId,
        @NotNull Long providerId,
        @NotNull Long fuelProductId,
        @NotNull Double quantity,
        @Size(max = 20) String unit,
        String source,
        @Size(max = 120) String episodeKey) {
}
