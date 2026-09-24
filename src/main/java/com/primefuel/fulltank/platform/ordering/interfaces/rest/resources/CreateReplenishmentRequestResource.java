package com.primefuel.fulltank.platform.ordering.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreateReplenishmentRequestResource(@NotNull Long providerId,
                                                 Long tankId,
                                                 @NotNull Long fuelProductId,
                                                 @NotNull @Positive Double quantity,
                                                 String unit,
                                                 @NotBlank String deliveryAddress,
                                                 @NotNull LocalDate deliveryDate) {
}
