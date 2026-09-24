package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.iam.application.ports.TenantMembershipAccess;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.OrganizationMembershipJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class TenantMembershipAccessAdapter implements TenantMembershipAccess, com.primefuel.fulltank.platform.iam.application.ports.TenantMembershipCommandStore {
    private final OrganizationMembershipJpaRepository repository;
    private final com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.OrganizationJpaRepository organizations;

    public TenantMembershipAccessAdapter(OrganizationMembershipJpaRepository repository,
                                         com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.OrganizationJpaRepository organizations) {
        this.repository = repository;
        this.organizations = organizations;
    }

    @Override
    public boolean hasActiveMemberships(Long userId) {
        return repository.hasActiveMemberships(userId);
    }

    @Override
    public boolean ownsBuyerCompany(Long userId, Long companyId) {
        return repository.ownsBuyerCompany(userId, companyId);
    }

    @Override
    public boolean ownsProviderCompany(Long userId, Long providerId) {
        return repository.ownsProviderCompany(userId, providerId);
    }

    @Override
    public boolean activate(Long providerId, Long userId, String role) {
        var organization = organizations.findByLegacyProviderCompanyId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider organization not found"));
        var membership = repository.findByUserIdAndOrganizationId(userId, organization.getId())
                .orElseGet(com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.MembershipPersistenceEntity::new);
        membership.setUserId(userId);
        membership.setOrganizationId(organization.getId());
        membership.setRole(role);
        membership.setStatus("ACTIVE");
        repository.save(membership);
        return true;
    }

    @Override
    public boolean revoke(Long providerId, Long userId) {
        var organization = organizations.findByLegacyProviderCompanyId(providerId).orElse(null);
        if (organization == null) return false;
        var membership = repository.findByUserIdAndOrganizationId(userId, organization.getId()).orElse(null);
        if (membership == null) return false;
        membership.setStatus("REVOKED");
        repository.save(membership);
        return true;
    }
}
