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
@Table(name = "device_bindings")
@Getter
@Setter
@NoArgsConstructor
public class DeviceBindingPersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(name = "device_id", nullable = false, length = 160)
    private String deviceId;
    @Column(nullable = false, length = 80)
    private String channel;
    @Column(name = "tank_id", nullable = false)
    private Long tankId;
    @Column(name = "buyer_company_id", nullable = false)
    private Long buyerCompanyId;
    @Column(name = "credential_hash", nullable = false, length = 128)
    private String credentialHash;
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;
    @Column(name = "valid_until")
    private Instant validUntil;
    @Column(nullable = false, length = 20)
    private String status;
}
