package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "telemetry_checkpoints")
@Getter
@Setter
@NoArgsConstructor
public class TelemetryCheckpointPersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(name = "device_id", nullable = false, length = 160) private String deviceId;
    @Column(nullable = false, length = 80) private String channel;
    @Column(name = "last_sequence_number", nullable = false) private long lastSequenceNumber;
}
