package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import jakarta.validation.constraints.*;

public record RegisterProviderTankResource(
        @NotNull @Positive Long buyerCompanyId,
        @NotNull @Positive Long customerAccountId,
        @NotNull @Positive Long siteId,
        @NotBlank @Size(max = 150) String name,
        @NotNull @Positive Long fuelProductId,
        @NotNull @Positive Double capacity,
        @NotBlank @Pattern(regexp = "LITRE|GALLON") String unit,
        @PositiveOrZero Double initialLevel,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("90")
                Double lowLevelPercent,
        @NotBlank @Size(max = 120) String deviceId,
        @NotBlank @Size(max = 60) String channel,
        Boolean autoGenerateEnabled) {}
