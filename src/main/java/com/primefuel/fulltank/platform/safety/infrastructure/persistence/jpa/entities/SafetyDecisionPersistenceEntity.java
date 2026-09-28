package com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "safety_decisions")
@Getter
@Setter
@NoArgsConstructor
public class SafetyDecisionPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "delivery_id", nullable = false)
    private Long deliveryId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "policy_version")
    private Integer policyVersion;

    @Column(nullable = false)
    private boolean authorized;

    @Column(length = 30)
    private String reason;

    @Column(name = "tracking_evidence_id")
    private Long trackingEvidenceId;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;
}
