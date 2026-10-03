package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import jakarta.validation.constraints.*;

public record RegisterProviderBuyerResource(
        @Positive Long buyerCompanyId,
        @Size(min = 1, max = 150) String name,
        @Pattern(regexp = "[0-9]{11}") String ruc,
        @Size(max = 100) String sector,
        @Size(max = 255) String address,
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 255) String phone,
        @Size(min = 1, max = 150) String siteName) {}
