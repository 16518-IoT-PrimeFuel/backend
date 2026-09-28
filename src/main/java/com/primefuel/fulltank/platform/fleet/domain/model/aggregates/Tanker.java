package com.primefuel.fulltank.platform.fleet.domain.model.aggregates;

import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.TankerStatus;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A tanker (the fuel truck) owned by a provider (the tenant). Same lifecycle rules as {@link Driver}:
 * typed status, soft-disable instead of delete, and no tenant transfer on update.
 */
@Getter
@Setter
@NoArgsConstructor
public class Tanker extends AbstractDomainAggregateRoot<Tanker> {

    private Long id;
    private Long providerId;
    private String licensePlate;
    private String brand;
    private String model;
    private Double capacity;
    private String unit;
    private String status;
    private boolean active = true;
    private Instant deactivatedAt;

    public Tanker(RegisterTankerCommand command) {
        if (command.providerId() == null) {
            throw new IllegalArgumentException("A provider is required");
        }
        this.providerId = command.providerId();
        apply(command.licensePlate(), command.brand(), command.model(),
                command.capacity(), command.unit(), command.status());
        this.active = true;
    }

    public void update(UpdateTankerCommand command) {
        apply(command.licensePlate(), command.brand(), command.model(),
                command.capacity(), command.unit(), command.status());
    }

    public void deactivate() {
        this.active = false;
        this.deactivatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.deactivatedAt = null;
    }

    private void apply(String licensePlate, String brand, String model,
                       Double capacity, String unit, String status) {
        this.licensePlate = require(licensePlate, "licensePlate");
        this.brand = require(brand, "brand");
        this.model = require(model, "model");
        if (capacity == null || capacity <= 0) {
            throw new IllegalArgumentException("Tanker capacity must be positive");
        }
        this.capacity = capacity;
        this.unit = unit == null || unit.isBlank() ? "LITERS" : unit;
        this.status = TankerStatus.fromCode(status).name();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A tanker " + field + " is required");
        }
        return value;
    }
}
