package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillPolicy;
import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.entities.RefillPolicyPersistenceEntity;

public final class RefillPolicyPersistenceAssembler {

    private RefillPolicyPersistenceAssembler() {
    }

    public static RefillPolicy toDomainFromPersistence(RefillPolicyPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new RefillPolicy();
        domain.setId(entity.getId());
        domain.setTankId(entity.getTankId());
        domain.setOrganizationId(entity.getOrganizationId());
        domain.setLowLevelPercent(entity.getLowLevelPercent());
        domain.setHysteresisPercent(entity.getHysteresisPercent());
        domain.setTargetLevelPercent(entity.getTargetLevelPercent());
        domain.setProviderId(entity.getProviderId());
        domain.setFuelProductId(entity.getFuelProductId());
        domain.setAutoGenerateEnabled(entity.isAutoGenerateEnabled());
        domain.setPolicyVersion(entity.getPolicyVersion());
        domain.setVersion(entity.getVersion());
        return domain;
    }

    public static RefillPolicyPersistenceEntity toPersistenceFromDomain(RefillPolicy domain) {
        if (domain == null) return null;
        var entity = new RefillPolicyPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setTankId(domain.getTankId());
        entity.setOrganizationId(domain.getOrganizationId());
        entity.setLowLevelPercent(domain.getLowLevelPercent());
        entity.setHysteresisPercent(domain.getHysteresisPercent());
        entity.setTargetLevelPercent(domain.getTargetLevelPercent());
        entity.setProviderId(domain.getProviderId());
        entity.setFuelProductId(domain.getFuelProductId());
        entity.setAutoGenerateEnabled(domain.isAutoGenerateEnabled());
        entity.setPolicyVersion(domain.getPolicyVersion());
        entity.setVersion(domain.getVersion());
        return entity;
    }
}
