package com.primefuel.fulltank.platform.iam.interfaces.rest.resources;

public record OrganizationResource(
        Long id,
        String name,
        String type,
        String role) {
}
