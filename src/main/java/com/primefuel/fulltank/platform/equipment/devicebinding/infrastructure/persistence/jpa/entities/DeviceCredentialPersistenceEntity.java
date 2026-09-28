package com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.valueobjects.CredentialStatus;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
        name = "device_credentials",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_device_credentials_token_hash", columnNames = "token_hash"))
@Getter
@Setter
@NoArgsConstructor
public class DeviceCredentialPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Column(nullable = false, length = 60)
    private String channel;

    /** SHA-256 hex of the token: the raw secret is never stored. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private CredentialStatus status;

    @Column(name = "created_at_credential", nullable = false)
    private Instant issuedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
