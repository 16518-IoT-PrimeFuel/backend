package com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services;

import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("tenantAccess")
public class TenantAccessImpl implements TenantAccess {

    private final CurrentUserAccess currentUserAccess;

    public TenantAccessImpl(CurrentUserAccess currentUserAccess) {
        this.currentUserAccess = currentUserAccess;
    }

    @Override
    public boolean ownsCompany(Long companyId) {
        return currentUserAccess.ownsCompany(companyId);
    }

    @Override
    public boolean ownsProvider(Long providerId) {
        return currentUserAccess.ownsProvider(providerId);
    }

    @Override
    public boolean ownsUser(Long userId) {
        return currentUserAccess.ownsUser(userId);
    }

    @Override
    public boolean ownsCompanyOrProvider(Long companyId, Long providerId) {
        return currentUserAccess.ownsCompanyOrProvider(companyId, providerId);
    }

    @Override
    public boolean isBuyerRole() {
        return currentUserAccess.isBuyerRole();
    }

    @Override
    public Optional<Long> currentProviderId() {
        return currentUserAccess.currentProviderId();
    }
}
