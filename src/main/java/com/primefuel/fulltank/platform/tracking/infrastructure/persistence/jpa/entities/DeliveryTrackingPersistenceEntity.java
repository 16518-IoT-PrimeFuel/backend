package com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
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
        name = "delivery_tracking",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_delivery_tracking_delivery", columnNames = "delivery_id"))
@Getter
@Setter
@NoArgsConstructor
public class DeliveryTrackingPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "delivery_id", nullable = false)
    private Long deliveryId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "last_latitude")
    private Double lastLatitude;

    @Column(name = "last_longitude")
    private Double lastLongitude;

    @Column(name = "last_accuracy_meters")
    private Double lastAccuracyMeters;

    @Column(name = "last_position_at")
    private Instant lastPositionAt;

    @Column(name = "last_position_evidence_id")
    private Long lastPositionEvidenceId;

    @Column(nullable = false)
    private boolean loaded;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_load_milestone", length = 20)
    private LoadMilestone lastLoadMilestone;

    @Column(name = "last_load_at")
    private Instant lastLoadAt;

    @Column(name = "last_load_volume")
    private Double lastLoadVolume;

    @Column(name = "last_load_unit", length = 20)
    private String lastLoadUnit;

    @Column(name = "last_load_evidence_id")
    private Long lastLoadEvidenceId;

    @Version
    @Column(nullable = false)
    private int version;
}
