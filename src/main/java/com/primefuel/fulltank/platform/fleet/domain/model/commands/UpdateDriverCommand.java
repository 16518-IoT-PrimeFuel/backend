package com.primefuel.fulltank.platform.fleet.domain.model.commands;

/**
 * Updates a driver's mutable data. It deliberately carries no provider: an update must never transfer the
 * tenant (S12 invariant), so ownership can only change through an explicit future operation.
 */
public record UpdateDriverCommand(
        Long driverId,
        String firstName,
        String lastName,
        String licenseNumber,
        String phoneNumber,
        String email,
        String status) {
}
