package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

import com.primefuel.fulltank.platform.shared.interfaces.rest.validation.ValidRuc;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateBuyerCompanyResource(@NotBlank String name,
                                         @NotBlank @Pattern(regexp = "\\d{11}") @ValidRuc String ruc,
                                         @NotBlank String sector,
                                         @NotBlank String address,
                                         @NotBlank @Email String contactEmail,
                                         @NotBlank String phone) {
}
