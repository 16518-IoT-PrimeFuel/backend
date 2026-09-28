package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerResource(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 11) String ruc,
        String address,
        @Email String contactEmail,
        String phone,
        Long legacyCompanyId) {
}
