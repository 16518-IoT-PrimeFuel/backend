package com.primefuel.fulltank.platform.fleet.domain.model.aggregates;

import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.DriverStatus;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A driver owned by a provider (the tenant). It is the fleet catalog entry behind eligibility: it carries
 * an explicit lifecycle ({@code active}) so it is disabled, never deleted (S12: "desactivar no borra
 * histórico"), and it keeps {@code userId} distinct from its own identity (never merged, T12-A).
 */
@Getter
@Setter
@NoArgsConstructor
public class Driver extends AbstractDomainAggregateRoot<Driver> {

    private Long id;
    private Long providerId;
    private Long userId;
    private String firstName;
    private String lastName;
    private String licenseNumber;
    private String phoneNumber;
    private String email;
    private String status;
    private boolean active = true;
    private Instant deactivatedAt;

    public Driver(RegisterDriverCommand command) {
        if (command.providerId() == null) {
            throw new IllegalArgumentException("A provider is required");
        }
        this.providerId = command.providerId();
        this.userId = command.userId();
        apply(command.firstName(), command.lastName(), command.licenseNumber(),
                command.phoneNumber(), command.email(), command.status());
        this.active = true;
    }

    /** Updates mutable data. The tenant ({@code providerId}) and the optional {@code userId} link are kept. */
    public void update(UpdateDriverCommand command) {
        apply(command.firstName(), command.lastName(), command.licenseNumber(),
                command.phoneNumber(), command.email(), command.status());
    }

    public void deactivate() {
        this.active = false;
        this.deactivatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.deactivatedAt = null;
    }

    private void apply(String firstName, String lastName, String licenseNumber,
                       String phoneNumber, String email, String status) {
        this.firstName = require(firstName, "firstName");
        this.lastName = require(lastName, "lastName");
        this.licenseNumber = require(licenseNumber, "licenseNumber");
        this.phoneNumber = require(phoneNumber, "phoneNumber");
        this.email = require(email, "email");
        this.status = DriverStatus.fromCode(status).name();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A driver " + field + " is required");
        }
        return value;
    }
}
