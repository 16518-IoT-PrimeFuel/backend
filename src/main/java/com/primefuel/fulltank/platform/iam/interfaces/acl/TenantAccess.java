package com.primefuel.fulltank.platform.iam.interfaces.acl;

/**
 * Public access contract exposed by IAM to other bounded contexts.
 *
 * The legacy implementation still resolves company/provider ids from the
 * authenticated principal. S04 will replace that resolution with memberships
 * without changing consumers of this interface.
 */
public interface TenantAccess {

    boolean isBuyer();

    boolean isProvider();

    boolean isBuyerRole();

    boolean isProviderRole();

    boolean ownsCompany(Long companyId);

    boolean ownsProvider(Long providerId);

    boolean ownsUser(Long userId);

    boolean ownsCompanyOrProvider(Long companyId, Long providerId);
}
