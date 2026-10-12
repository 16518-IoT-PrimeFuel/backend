package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

import java.util.List;
import com.primefuel.fulltank.platform.shared.interfaces.rest.validation.ValidRuc;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record CreateProviderCompanyResource(@NotBlank String name,
                                            @NotBlank @Pattern(regexp = "\\d{11}") @ValidRuc String ruc,
                                            Double rating,
                                            @NotBlank String address,
                                            @NotBlank String phone,
                                            @NotEmpty List<String> fuelTypesOffered,
                                            String description) {
}
