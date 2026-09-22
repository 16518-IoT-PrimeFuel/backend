package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerAccount;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.CustomerAccountPersistenceEntity;

public final class CustomerAccountPersistenceAssembler {

    private CustomerAccountPersistenceAssembler() {
    }

    public static CustomerAccount toDomainFromPersistence(CustomerAccountPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new CustomerAccount();
        domain.setId(entity.getId());
        domain.setOrganizationId(entity.getOrganizationId());
        domain.setName(entity.getName());
        domain.setRuc(entity.getRuc());
        domain.setAddress(entity.getAddress());
        domain.setContactEmail(entity.getContactEmail());
        domain.setPhone(entity.getPhone());
        domain.setLegacyCompanyId(entity.getLegacyCompanyId());
        domain.setActive(entity.isActive());
        return domain;
    }

    public static CustomerAccountPersistenceEntity toPersistenceFromDomain(CustomerAccount domain) {
        if (domain == null) return null;
        var entity = new CustomerAccountPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setOrganizationId(domain.getOrganizationId());
        entity.setName(domain.getName());
        entity.setRuc(domain.getRuc());
        entity.setAddress(domain.getAddress());
        entity.setContactEmail(domain.getContactEmail());
        entity.setPhone(domain.getPhone());
        entity.setLegacyCompanyId(domain.getLegacyCompanyId());
        entity.setActive(domain.isActive());
        return entity;
    }
}
