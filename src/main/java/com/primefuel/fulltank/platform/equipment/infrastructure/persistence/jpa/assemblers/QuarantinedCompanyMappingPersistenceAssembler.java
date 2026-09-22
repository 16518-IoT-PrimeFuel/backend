package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.QuarantinedCompanyMapping;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.QuarantinedCompanyMappingPersistenceEntity;

public final class QuarantinedCompanyMappingPersistenceAssembler {

    private QuarantinedCompanyMappingPersistenceAssembler() {
    }

    public static QuarantinedCompanyMapping toDomainFromPersistence(QuarantinedCompanyMappingPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new QuarantinedCompanyMapping();
        domain.setId(entity.getId());
        domain.setLegacyCompanyId(entity.getLegacyCompanyId());
        domain.setRuc(entity.getRuc());
        domain.setReason(entity.getReason());
        return domain;
    }

    public static QuarantinedCompanyMappingPersistenceEntity toPersistenceFromDomain(QuarantinedCompanyMapping domain) {
        if (domain == null) return null;
        var entity = new QuarantinedCompanyMappingPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setLegacyCompanyId(domain.getLegacyCompanyId());
        entity.setRuc(domain.getRuc());
        entity.setReason(domain.getReason());
        return entity;
    }
}
