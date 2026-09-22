package com.primefuel.fulltank.platform.equipment.api;

import java.util.Optional;

public interface CustomerDirectory {

    boolean ownsCustomer(Long organizationId, Long customerAccountId);

    Optional<Long> customerIdForLegacyCompany(Long legacyCompanyId);
}
