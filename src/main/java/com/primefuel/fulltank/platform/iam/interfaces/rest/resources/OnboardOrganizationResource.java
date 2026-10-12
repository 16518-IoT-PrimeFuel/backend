package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

import com.primefuel.fulltank.platform.shared.interfaces.rest.validation.ValidRuc;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OnboardOrganizationResource(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 11) @Pattern(regexp = "[0-9]{11}") @ValidRuc String ruc,
        @NotBlank String type) {
}
