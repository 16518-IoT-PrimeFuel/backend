package com.primefuel.fulltank.platform.iam.application.ports;

public interface TenantMembershipAccess {
    boolean hasActiveMemberships(Long userId);

    boolean ownsBuyerCompany(Long userId, Long companyId);

    boolean ownsProviderCompany(Long userId, Long providerId);
}
