package com.primefuel.fulltank.platform.tracking.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.TransportEvidenceKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "transport_evidence_samples",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tes_delivery_client_event",
                columnNames = {"delivery_id", "client_event_id"}))
@Getter
@Setter
@NoArgsConstructor
public class TransportEvidenceSamplePersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "delivery_id", nullable = false)
    private Long deliveryId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "driver_id")
    private Long driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransportEvidenceKind kind;

    private Double latitude;

    private Double longitude;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(length = 20)
    private String milestone;

    private Double volume;

    @Column(length = 20)
    private String unit;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "client_event_id", length = 160)
    private String clientEventId;

    @Column(name = "latest_advanced", nullable = false)
    private boolean latestAdvanced;
}
