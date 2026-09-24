package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "telemetry_inbox")
@Getter
@Setter
@NoArgsConstructor
public class TelemetryInboxPersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(name = "event_id", nullable = false, unique = true, length = 160) private String eventId;
    @Column(name = "device_id", nullable = false, length = 160) private String deviceId;
    @Column(nullable = false, length = 80) private String channel;
    @Column(name = "sequence_number", nullable = false) private long sequenceNumber;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private int attempts;
    @Column(name = "last_error", length = 255) private String lastError;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
}
