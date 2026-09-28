package com.primefuel.fulltank.platform.equipment.domain.model.aggregates;

import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Immutable-per-version snapshot of a tank's configuration (capacity/unit/fuel type). */
@Getter
@Setter
@NoArgsConstructor
public class TankConfiguration extends AbstractDomainAggregateRoot<TankConfiguration> {

    private Long id;
    private Long tankId;
    private int version;
    private String fuelType;
    private Volume capacity;
    private Instant recordedAt;

    public TankConfiguration(Long tankId, int version, String fuelType, Volume capacity) {
        this.tankId = tankId;
        this.version = version;
        this.fuelType = fuelType;
        this.capacity = capacity;
        this.recordedAt = Instant.now();
    }
}
