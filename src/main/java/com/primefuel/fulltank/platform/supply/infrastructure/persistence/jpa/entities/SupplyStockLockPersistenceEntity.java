package com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "supply_stock_locks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_supply_stock_locks_provider_product", columnNames = {"provider_id", "fuel_product_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SupplyStockLockPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "fuel_product_id", nullable = false)
    private Long fuelProductId;

    public SupplyStockLockPersistenceEntity(Long providerId, Long fuelProductId) {
        this.providerId = providerId;
        this.fuelProductId = fuelProductId;
    }
}
