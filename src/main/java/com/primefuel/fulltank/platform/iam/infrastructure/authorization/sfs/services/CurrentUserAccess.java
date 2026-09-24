package com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services;

import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.iam.application.ports.TenantMembershipAccess;
import com.primefuel.fulltank.platform.iam.interfaces.acl.TenantAccess;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("currentUserAccess")
public class CurrentUserAccess implements TenantAccess {
    private final TenantMembershipAccess memberships;

    public CurrentUserAccess() {
        this.memberships = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CurrentUserAccess(TenantMembershipAccess memberships) {
        this.memberships = memberships;
    }

    public boolean isBuyer() {
        return hasAuthority("ROLE_BUYER") && current().getCompanyId() != null;
    }

    public boolean isProvider() {
        return hasAuthority("ROLE_PROVIDER") && current().getProviderId() != null;
    }

    public boolean ownsCompany(Long companyId) {
        if (!isBuyer() || companyId == null) return false;
        return usesMemberships() ? memberships.ownsBuyerCompany(current().getUserId(), companyId)
                : companyId.equals(current().getCompanyId());
    }

    public boolean ownsProvider(Long providerId) {
        if (!isProvider() || providerId == null) return false;
        return usesMemberships() ? memberships.ownsProviderCompany(current().getUserId(), providerId)
                : providerId.equals(current().getProviderId());
    }

    public boolean ownsUser(Long userId) {
        return userId != null && userId.equals(current().getUserId());
    }

    public boolean ownsCompanyOrProvider(Long companyId, Long providerId) {
        return ownsCompany(companyId) || ownsProvider(providerId);
    }

    public boolean isBuyerRole() {
        return hasAuthority("ROLE_BUYER");
    }

    public boolean isProviderRole() {
        return hasAuthority("ROLE_PROVIDER");
    }

    private boolean hasAuthority(String authority) {
        var principal = principal();
        return principal != null && principal.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private UserDetailsImpl current() {
        var principal = principal();
        if (principal == null) throw new IllegalStateException("Authenticated FullTank user required");
        return principal;
    }

    private UserDetailsImpl principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl principal
                ? principal : null;
    }

    private boolean usesMemberships() {
        return memberships != null && memberships.hasActiveMemberships(current().getUserId());
    }
}
