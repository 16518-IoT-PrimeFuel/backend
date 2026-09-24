package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tanks")
@Getter
@Setter
@NoArgsConstructor
public class TankPersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(name = "customer_site_id", nullable = false)
    private Long customerSiteId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "fuel_type", nullable = false, length = 20)
    private String fuelType;

    @Column(nullable = false)
    private Double capacity;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "current_level", nullable = false)
    private Double currentLevel;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "last_reading_at")
    private Instant lastReadingAt;
}
