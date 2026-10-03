package com.primefuel.fulltank.platform.iam.api;

import java.util.Optional;

/** Read-only company identity, with a membership-verified organization mapping. */
public interface BuyerCompanyDirectory {
    Optional<BuyerSnapshot> findById(Long companyId);
    Optional<BuyerIdentity> findByRuc(String ruc);

    record BuyerIdentity(Long id, String name, String ruc) {}

    record BuyerSnapshot(Long id, String name, Long organizationId, String ruc, String sector) {}
}
