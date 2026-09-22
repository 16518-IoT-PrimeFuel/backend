package com.primefuel.fulltank.platform.equipment.domain.model.aggregates;

import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.UpdateTankConfigurationCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.TankClassification;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class Tank extends AbstractDomainAggregateRoot<Tank> {

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_VALIDATED = "VALIDATED";

    private Long id;
    private Long organizationId;
    private Long customerAccountId;
    private Long siteId;
    private String name;
    private String fuelType;
    private TankClassification classification;
    private Long legacyEquipmentId;
    private int configurationVersion;
    private Volume capacity;
    private Volume currentLevel;
    private Instant levelObservedAt;
    private String levelSource;
    private boolean active = true;

    public Tank(RegisterTankCommand command) {
        if (command.capacity() == null || command.capacity() <= 0) {
            throw new IllegalArgumentException("Tank capacity must be positive");
        }
        this.organizationId = command.organizationId();
        this.customerAccountId = command.customerAccountId();
        this.siteId = command.siteId();
        this.name = command.name();
        this.fuelType = command.fuelType();
        this.legacyEquipmentId = command.legacyEquipmentId();
        this.classification = command.legacyEquipmentId() == null
                ? TankClassification.NATIVE : TankClassification.LEGACY_MAPPABLE;
        this.configurationVersion = 1;
        this.capacity = Volume.of(command.capacity(), Unit.fromCode(command.unit()));
        var initial = command.initialLevel() == null ? 0.0 : command.initialLevel();
        this.currentLevel = Volume.of(initial, this.capacity.unit());
        if (this.currentLevel.exceeds(this.capacity)) {
            throw new IllegalArgumentException("Tank level cannot exceed capacity");
        }
        this.levelSource = SOURCE_MANUAL;
        this.levelObservedAt = Instant.now();
    }

    public void applyConfiguration(UpdateTankConfigurationCommand command) {
        if (command.capacity() == null || command.capacity() <= 0) {
            throw new IllegalArgumentException("Tank capacity must be positive");
        }
        var newCapacity = Volume.of(command.capacity(), Unit.fromCode(command.unit()));
        var levelInNewUnit = currentLevel.convertedTo(newCapacity.unit());
        if (levelInNewUnit.exceeds(newCapacity)) {
            throw new IllegalArgumentException("Tank level cannot exceed the new capacity");
        }
        this.capacity = newCapacity;
        this.currentLevel = levelInNewUnit;
        this.fuelType = command.fuelType();
        this.configurationVersion = this.configurationVersion + 1;
    }

    /** Manual metadata edit: does not pretend to be a measured reading. */
    public void updateLevelManually(Volume level) {
        applyLevel(level, Instant.now(), SOURCE_MANUAL);
    }

    /** Applies a validated telemetry reading, ignoring out-of-order (older) observations. */
    public boolean applyValidatedReading(Volume level, Instant observedAt) {
        if (observedAt == null) {
            throw new IllegalArgumentException("An observation instant is required");
        }
        if (levelObservedAt != null && !observedAt.isAfter(levelObservedAt)) {
            return false;
        }
        applyLevel(level, observedAt, SOURCE_VALIDATED);
        return true;
    }

    private void applyLevel(Volume level, Instant observedAt, String source) {
        if (level.exceeds(capacity)) {
            throw new IllegalArgumentException("Tank level cannot exceed capacity");
        }
        this.currentLevel = level.convertedTo(capacity.unit());
        this.levelObservedAt = observedAt;
        this.levelSource = source;
    }

    public void deactivate() {
        this.active = false;
    }
}
