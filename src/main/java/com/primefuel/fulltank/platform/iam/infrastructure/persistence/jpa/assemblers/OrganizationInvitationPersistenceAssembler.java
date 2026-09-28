package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.OrganizationInvitation;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.OrganizationInvitationPersistenceEntity;

public final class OrganizationInvitationPersistenceAssembler {

    private OrganizationInvitationPersistenceAssembler() {
    }

    public static OrganizationInvitation toDomainFromPersistence(OrganizationInvitationPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new OrganizationInvitation();
        domain.setId(entity.getId());
        domain.setOrganizationId(entity.getOrganizationId());
        domain.setEmail(entity.getEmail());
        domain.setRole(entity.getRole());
        domain.setToken(entity.getToken());
        domain.setStatus(entity.getStatus());
        domain.setExpiresAt(entity.getExpiresAt());
        domain.setInvitedByUserId(entity.getInvitedByUserId());
        return domain;
    }

    public static OrganizationInvitationPersistenceEntity toPersistenceFromDomain(OrganizationInvitation domain) {
        if (domain == null) return null;
        var entity = new OrganizationInvitationPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setOrganizationId(domain.getOrganizationId());
        entity.setEmail(domain.getEmail());
        entity.setRole(domain.getRole());
        entity.setToken(domain.getToken());
        entity.setStatus(domain.getStatus());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setInvitedByUserId(domain.getInvitedByUserId());
        return entity;
    }
}
