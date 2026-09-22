package com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceCredential;
import com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.entities.DeviceCredentialPersistenceEntity;

public final class DeviceCredentialPersistenceAssembler {

    private DeviceCredentialPersistenceAssembler() {
    }

    public static DeviceCredential toDomainFromPersistence(DeviceCredentialPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new DeviceCredential();
        domain.setId(entity.getId());
        domain.setDeviceId(entity.getDeviceId());
        domain.setChannel(entity.getChannel());
        domain.setTokenHash(entity.getTokenHash());
        domain.setTokenVersion(entity.getTokenVersion());
        domain.setStatus(entity.getStatus());
        domain.setCreatedAt(entity.getIssuedAt());
        domain.setRevokedAt(entity.getRevokedAt());
        return domain;
    }

    public static DeviceCredentialPersistenceEntity toPersistenceFromDomain(DeviceCredential domain) {
        if (domain == null) return null;
        var entity = new DeviceCredentialPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setDeviceId(domain.getDeviceId());
        entity.setChannel(domain.getChannel());
        entity.setTokenHash(domain.getTokenHash());
        entity.setTokenVersion(domain.getTokenVersion());
        entity.setStatus(domain.getStatus());
        entity.setIssuedAt(domain.getCreatedAt());
        entity.setRevokedAt(domain.getRevokedAt());
        return entity;
    }
}
