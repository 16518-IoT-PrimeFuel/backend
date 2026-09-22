package com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.entities.DeviceBindingPersistenceEntity;

public final class DeviceBindingPersistenceAssembler {

    private DeviceBindingPersistenceAssembler() {
    }

    public static DeviceBinding toDomainFromPersistence(DeviceBindingPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new DeviceBinding();
        domain.setId(entity.getId());
        domain.setOrganizationId(entity.getOrganizationId());
        domain.setDeviceId(entity.getDeviceId());
        domain.setChannel(entity.getChannel());
        domain.setTankId(entity.getTankId());
        domain.setValidFrom(entity.getValidFrom());
        domain.setValidTo(entity.getValidTo());
        domain.setStatus(entity.getStatus());
        domain.setActiveSlot(entity.getActiveSlot());
        return domain;
    }

    public static DeviceBindingPersistenceEntity toPersistenceFromDomain(DeviceBinding domain) {
        if (domain == null) return null;
        var entity = new DeviceBindingPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setOrganizationId(domain.getOrganizationId());
        entity.setDeviceId(domain.getDeviceId());
        entity.setChannel(domain.getChannel());
        entity.setTankId(domain.getTankId());
        entity.setValidFrom(domain.getValidFrom());
        entity.setValidTo(domain.getValidTo());
        entity.setStatus(domain.getStatus());
        entity.setActiveSlot(domain.getActiveSlot());
        return entity;
    }
}
