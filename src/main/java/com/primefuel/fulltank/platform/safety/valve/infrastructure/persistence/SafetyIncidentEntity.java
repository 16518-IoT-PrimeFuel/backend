package com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "safety_incidents", indexes = @Index(name = "ix_safety_incidents_delivery_time", columnList = "delivery_id,occurred_at"))
@Getter @Setter @NoArgsConstructor
public class SafetyIncidentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "delivery_id", nullable = false) private Long deliveryId;
    @Column(name = "provider_id", nullable = false) private Long providerId;
    @Column(nullable = false, length = 30) private String kind;
    @Column(name = "command_id", length = 36) private String commandId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;
}
