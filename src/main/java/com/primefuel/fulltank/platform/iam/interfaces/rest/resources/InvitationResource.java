package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

public record InvitationResource(
        Long id,
        Long organizationId,
        String email,
        String role,
        String status,
        String token,
        String expiresAt) {
}
