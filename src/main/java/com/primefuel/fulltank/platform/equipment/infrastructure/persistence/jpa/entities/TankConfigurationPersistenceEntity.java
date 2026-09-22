package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tank_configurations")
@Getter
@Setter
@NoArgsConstructor
public class TankConfigurationPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "tank_id", nullable = false)
    private Long tankId;

    @Column(nullable = false)
    private int version;

    @Column(name = "fuel_type", length = 30)
    private String fuelType;

    @Column(name = "capacity_amount", nullable = false)
    private double capacityAmount;

    @Column(name = "capacity_unit", nullable = false, length = 20)
    private String capacityUnit;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
