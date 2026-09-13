package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmResource(@NotBlank String token,
                                           @NotBlank @Size(min = 8, max = 120) String newPassword) {
}
