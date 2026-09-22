package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.TankClassification;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
        name = "tanks",
        uniqueConstraints = @UniqueConstraint(name = "uk_tanks_legacy_equipment", columnNames = "legacy_equipment_id"))
@Getter
@Setter
@NoArgsConstructor
public class TankPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "customer_account_id", nullable = false)
    private Long customerAccountId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "fuel_type", length = 30)
    private String fuelType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private TankClassification classification;

    @Column(name = "legacy_equipment_id")
    private Long legacyEquipmentId;

    @Column(name = "configuration_version", nullable = false)
    private int configurationVersion;

    @Column(name = "capacity_amount", nullable = false)
    private double capacityAmount;

    @Column(name = "capacity_unit", nullable = false, length = 20)
    private String capacityUnit;

    @Column(name = "level_amount", nullable = false)
    private double levelAmount;

    @Column(name = "level_unit", nullable = false, length = 20)
    private String levelUnit;

    @Column(name = "level_observed_at")
    private Instant levelObservedAt;

    @Column(name = "level_source", length = 20)
    private String levelSource;

    @Column(nullable = false)
    private boolean active;
}
