package com.primefuel.fulltank.platform.fleet.domain.model.commands;

/**
 * Registers a driver for a provider (the tenant). {@code userId} is an optional link to an IAM user and is
 * deliberately separate from the driver's own identity — the two are never merged (S12/T12-A).
 */
public record RegisterDriverCommand(
        Long providerId,
        Long userId,
        String firstName,
        String lastName,
        String licenseNumber,
        String phoneNumber,
        String email,
        String status) {
}
