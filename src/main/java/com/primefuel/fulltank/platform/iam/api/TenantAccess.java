package com.primefuel.fulltank.platform.iam.api;

public interface TenantAccess {

    boolean ownsCompany(Long companyId);

    boolean ownsProvider(Long providerId);

    boolean ownsUser(Long userId);

    boolean ownsCompanyOrProvider(Long companyId, Long providerId);

    boolean isBuyerRole();
}
