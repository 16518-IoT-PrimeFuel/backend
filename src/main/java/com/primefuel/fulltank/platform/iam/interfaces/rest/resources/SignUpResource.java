package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record SignUpResource(
        @NotBlank @Email @Size(max = 100) String username,
        @NotBlank @Size(min = 8, max = 120) String password,
        @NotEmpty List<String> roles,
        @Valid CreateBuyerCompanyResource buyerCompany,
        @Valid CreateProviderCompanyResource providerCompany) {
}
