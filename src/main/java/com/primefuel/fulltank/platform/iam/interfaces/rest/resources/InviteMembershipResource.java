package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteMembershipResource(@NotBlank @Email String username,
                                       @NotBlank String role) {
}
