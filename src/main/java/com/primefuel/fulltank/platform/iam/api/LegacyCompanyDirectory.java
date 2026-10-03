package com.primefuel.fulltank.platform.iam.api;

import java.util.Optional;

public interface LegacyCompanyDirectory {

    /** The legacy buyer company of an organization, matched by RUC (unique on both sides). */
    Optional<Long> buyerCompanyIdForOrganization(Long organizationId);

    Optional<Long> organizationIdForProvider(Long providerId);
}
