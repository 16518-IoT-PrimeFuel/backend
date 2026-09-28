package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerSite;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.CustomerSitePersistenceEntity;

public final class CustomerSitePersistenceAssembler {

    private CustomerSitePersistenceAssembler() {
    }

    public static CustomerSite toDomainFromPersistence(CustomerSitePersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new CustomerSite();
        domain.setId(entity.getId());
        domain.setOrganizationId(entity.getOrganizationId());
        domain.setCustomerAccountId(entity.getCustomerAccountId());
        domain.setName(entity.getName());
        domain.setAddress(entity.getAddress());
        domain.setActive(entity.isActive());
        return domain;
    }

    public static CustomerSitePersistenceEntity toPersistenceFromDomain(CustomerSite domain) {
        if (domain == null) return null;
        var entity = new CustomerSitePersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setOrganizationId(domain.getOrganizationId());
        entity.setCustomerAccountId(domain.getCustomerAccountId());
        entity.setName(domain.getName());
        entity.setAddress(domain.getAddress());
        entity.setActive(domain.isActive());
        return entity;
    }
}
