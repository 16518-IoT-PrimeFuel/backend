package com.primefuel.fulltank.platform.iam.api;

import java.util.Optional;

/** Read-only company identity, with a membership-verified organization mapping. */
public interface BuyerCompanyDirectory {
    Optional<BuyerSnapshot> findById(Long companyId);

    record BuyerSnapshot(Long id, String name, Long organizationId) {}
}
