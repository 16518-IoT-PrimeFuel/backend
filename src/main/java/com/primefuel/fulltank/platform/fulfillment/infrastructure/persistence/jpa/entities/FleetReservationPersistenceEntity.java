package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fleet_reservations")
@Getter
@Setter
@NoArgsConstructor
public class FleetReservationPersistenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long providerId;
    private Long deliveryId;
    @Column(nullable = false)
    private Long driverId;
    @Column(nullable = false)
    private Long vehicleId;
    @Column(nullable = false)
    private Instant windowStart;
    @Column(nullable = false)
    private Instant windowEnd;
    @Column(nullable = false)
    private Double volume;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant releasedAt;
}
