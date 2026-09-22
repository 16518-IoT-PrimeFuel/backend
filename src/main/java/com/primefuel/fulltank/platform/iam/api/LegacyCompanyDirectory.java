package com.primefuel.fulltank.platform.iam.api;

import java.util.List;
import java.util.Optional;

public interface LegacyCompanyDirectory {

    List<LegacyCompanySnapshot> findAllBuyerCompanies();

    Optional<Long> organizationIdForRuc(String ruc);

    record LegacyCompanySnapshot(
            Long id,
            String name,
            String ruc,
            String address,
            String contactEmail,
            String phone) {
    }
}
