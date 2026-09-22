package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.MembershipPersistenceEntity;

public final class MembershipPersistenceAssembler {

    private MembershipPersistenceAssembler() {
    }

    public static Membership toDomainFromPersistence(MembershipPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new Membership();
        domain.setId(entity.getId());
        domain.setOrganizationId(entity.getOrganizationId());
        domain.setUserId(entity.getUserId());
        domain.setRole(entity.getRole());
        domain.setActive(entity.isActive());
        return domain;
    }

    public static MembershipPersistenceEntity toPersistenceFromDomain(Membership domain) {
        if (domain == null) return null;
        var entity = new MembershipPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setOrganizationId(domain.getOrganizationId());
        entity.setUserId(domain.getUserId());
        entity.setRole(domain.getRole());
        entity.setActive(domain.isActive());
        return entity;
    }
}
