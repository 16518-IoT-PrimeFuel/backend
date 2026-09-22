package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OnboardOrganizationResource(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 11) String ruc,
        @NotBlank String type) {
}
