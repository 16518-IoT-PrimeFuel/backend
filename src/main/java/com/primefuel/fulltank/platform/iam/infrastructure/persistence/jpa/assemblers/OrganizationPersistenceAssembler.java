package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.OrganizationPersistenceEntity;

public final class OrganizationPersistenceAssembler {

    private OrganizationPersistenceAssembler() {
    }

    public static Organization toDomainFromPersistence(OrganizationPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new Organization();
        domain.setId(entity.getId());
        domain.setName(entity.getName());
        domain.setRuc(entity.getRuc());
        domain.setType(entity.getType());
        domain.setActive(entity.isActive());
        return domain;
    }

    public static OrganizationPersistenceEntity toPersistenceFromDomain(Organization domain) {
        if (domain == null) return null;
        var entity = new OrganizationPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setName(domain.getName());
        entity.setRuc(domain.getRuc());
        entity.setType(domain.getType());
        entity.setActive(domain.isActive());
        return entity;
    }
}
