package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Credencial técnica de un canal de dispositivo. {@code token} solo viene al emitir o rotar y se devuelve una
 * única vez; {@code revokedAt} solo viene al revocar.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceCredentialResource(
        Long tankId,
        String deviceId,
        String channel,
        Long credentialId,
        Integer tokenVersion,
        String token,
        Instant issuedAt,
        Instant revokedAt) {

    @Override
    public String toString() {
        return "DeviceCredentialResource[tankId=" + tankId + ", deviceId=" + deviceId + ", channel=" + channel
                + ", credentialId=" + credentialId + ", tokenVersion=" + tokenVersion
                + ", token=" + (token == null ? null : "****") + ", issuedAt=" + issuedAt
                + ", revokedAt=" + revokedAt + "]";
    }
}
