package com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import com.primefuel.fulltank.platform.supply.domain.model.valueobjects.ReservationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "supply_reservations")
@Getter
@Setter
@NoArgsConstructor
public class SupplyReservationPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "fuel_product_id", nullable = false)
    private Long fuelProductId;

    @Column(nullable = false, length = 120)
    private String reference;

    @Column(nullable = false)
    private double quantity;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price", nullable = false)
    private double unitPrice;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private ReservationStatus status;
}
