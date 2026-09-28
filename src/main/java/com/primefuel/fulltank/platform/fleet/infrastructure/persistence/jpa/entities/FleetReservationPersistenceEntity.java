package com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.FleetReservationStatus;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "fleet_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fleet_reservations_reference", columnNames = "reference"))
@Getter
@Setter
@NoArgsConstructor
public class FleetReservationPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "tanker_id", nullable = false)
    private Long tankerId;

    @Column(length = 120)
    private String reference;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(nullable = false)
    private Double volume;

    @Column(nullable = false, length = 20)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FleetReservationStatus status;

    @Version
    @Column(nullable = false)
    private int version;
}
