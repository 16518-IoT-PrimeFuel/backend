package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import jakarta.validation.constraints.*;

public record UpdateProviderTankResource(
        @Positive Long fuelProductId,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("90") Double lowLevelPercent,
        @Size(min = 1, max = 120) String deviceId,
        @Size(min = 1, max = 60) String channel,
        Boolean autoGenerateEnabled) {}
