package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.iam.application.ports.TenantMembershipAccess;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.OrganizationMembershipJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class TenantMembershipAccessAdapter implements TenantMembershipAccess {
    private final OrganizationMembershipJpaRepository repository;

    public TenantMembershipAccessAdapter(OrganizationMembershipJpaRepository repository) {
        this.repository = repository;
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
}
