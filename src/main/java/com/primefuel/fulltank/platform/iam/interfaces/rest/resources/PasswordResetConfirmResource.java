package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmResource(@NotBlank String token,
                                           @NotBlank @Size(min = 8, max = 120)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "must contain at least one letter and one digit") String newPassword) {
}
