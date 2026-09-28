package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "refill_policies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_refill_policies_tank", columnNames = "tank_id"))
@Getter
@Setter
@NoArgsConstructor
public class RefillPolicyPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "tank_id", nullable = false)
    private Long tankId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "low_level_percent", nullable = false)
    private double lowLevelPercent;

    @Column(name = "hysteresis_percent", nullable = false)
    private double hysteresisPercent;

    @Column(name = "target_level_percent", nullable = false)
    private double targetLevelPercent;

    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "fuel_product_id")
    private Long fuelProductId;

    @Column(name = "auto_generate_enabled", nullable = false)
    private boolean autoGenerateEnabled;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion;

    @Version
    @Column(nullable = false)
    private int version;
}
