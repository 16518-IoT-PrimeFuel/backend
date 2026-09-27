package com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateReplenishmentRequestResource(
        @NotNull Long customerAccountId,
        Long tankId,
        @NotNull Long providerId,
        @NotNull Long fuelProductId,
        @NotNull Double quantity,
        @Size(max = 20) String unit,
        String source,
        @Size(max = 120) String episodeKey,
        @Schema(description = "Dirección de descarga; si se omite o está vacía, se toma del sitio de la cisterna.")
        @Size(max = 255) String deliveryAddress,
        @Schema(description = "Fecha programada de entrega; no puede ser anterior al día actual en Lima.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate deliveryDate) {
}
