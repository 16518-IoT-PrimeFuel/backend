package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import com.primefuel.fulltank.platform.shared.interfaces.rest.validation.ValidRuc;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomerResource(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 11) @Pattern(regexp = "[0-9]{11}") @ValidRuc String ruc,
        String address,
        @Email String contactEmail,
        String phone,
        Long legacyCompanyId) {
}
