package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refill_policies")
@Getter
@Setter
@NoArgsConstructor
public class RefillPolicyPersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(name = "tank_id", nullable = false, unique = true) private Long tankId;
    @Column(name = "buyer_company_id", nullable = false) private Long buyerCompanyId;
    @Column(name = "provider_id", nullable = false) private Long providerId;
    @Column(name = "fuel_product_id", nullable = false) private Long fuelProductId;
    @Column(name = "threshold_value", nullable = false) private double thresholdValue;
    @Column(name = "hysteresis_value", nullable = false) private double hysteresisValue;
    @Column(name = "target_volume", nullable = false) private double targetVolume;
    @Column(name = "delivery_address", nullable = false) private String deliveryAddress;
    @Column(nullable = false) private boolean enabled;
}
