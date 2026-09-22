package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteMemberResource(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank String role) {
}
