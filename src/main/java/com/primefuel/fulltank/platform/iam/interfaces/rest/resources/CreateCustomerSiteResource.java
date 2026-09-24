package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record CreateCustomerSiteResource(@NotBlank String name,
                                         @NotBlank String address,
                                         String sector) {
}
