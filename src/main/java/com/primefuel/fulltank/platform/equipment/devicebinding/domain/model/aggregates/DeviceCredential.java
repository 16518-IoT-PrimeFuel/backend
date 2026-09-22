package com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.valueobjects.CredentialStatus;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Rotating technical credential for a device channel. Only the SHA-256 hash of the token is stored: the
 * raw token is returned exactly once at provisioning/rotation time and is never persisted, logged or put
 * on an event.
 */
@Getter
@Setter
@NoArgsConstructor
public class DeviceCredential extends AbstractDomainAggregateRoot<DeviceCredential> {

    private Long id;
    private String deviceId;
    private String channel;
    private String tokenHash;
    private int tokenVersion;
    private CredentialStatus status;
    private Instant createdAt;
    private Instant revokedAt;

    public DeviceCredential(String deviceId, String channel, String tokenHash, int tokenVersion, Instant createdAt) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("A device id is required");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("A device channel is required");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("A token hash is required");
        }
        this.deviceId = deviceId;
        this.channel = channel;
        this.tokenHash = tokenHash;
        this.tokenVersion = tokenVersion;
        this.status = CredentialStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return status == CredentialStatus.ACTIVE;
    }

    public void revoke(Instant at) {
        if (!isActive()) {
            throw new IllegalStateException("Only an active credential can be revoked");
        }
        this.status = CredentialStatus.REVOKED;
        this.revokedAt = at;
    }
}
