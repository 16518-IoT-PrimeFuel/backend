package com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "valve_commands", uniqueConstraints = {
        @UniqueConstraint(name = "uk_valve_commands_delivery_command", columnNames = {"delivery_id", "command_id"}),
        @UniqueConstraint(name = "uk_valve_commands_delivery_nonce", columnNames = {"delivery_id", "nonce"}),
        @UniqueConstraint(name = "uk_valve_commands_delivery_decision", columnNames = {"delivery_id", "decision_id"})
}, indexes = @Index(name = "ix_valve_commands_delivery_status", columnList = "delivery_id,status"))
@Getter @Setter @NoArgsConstructor
public class ValveCommandEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "command_id", nullable = false, length = 36) private String commandId;
    @Column(name = "delivery_id", nullable = false) private Long deliveryId;
    @Column(name = "provider_id", nullable = false) private Long providerId;
    @Column(name = "decision_id", nullable = false) private Long decisionId;
    @Column(nullable = false, length = 10) private String action;
    @Column(nullable = false, length = 36) private String nonce;
    @Column(name = "issued_at", nullable = false) private Instant issuedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(nullable = false, length = 64) private String signature;
    @Column(name = "protocol_version", nullable = false, length = 5) private String protocolVersion;
    @Column(nullable = false, length = 12) private String status;
    @Column(name = "acked_at") private Instant ackedAt;
    @Version @Column(nullable = false) private long version;
}
