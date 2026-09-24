package com.primefuel.fulltank.platform.inventory.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "supply_reservations")
@Getter
@Setter
@NoArgsConstructor
public class SupplyReservationPersistenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true)
    private Long requestId;

    @Column(name = "fuel_product_id", nullable = false)
    private Long fuelProductId;

    @Column(nullable = false)
    private Double quantity;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;
}
