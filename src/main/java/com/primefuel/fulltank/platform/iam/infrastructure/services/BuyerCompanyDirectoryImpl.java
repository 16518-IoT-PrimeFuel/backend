package com.primefuel.fulltank.platform.iam.infrastructure.services;

import com.primefuel.fulltank.platform.iam.api.BuyerCompanyDirectory;
import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationRepository;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BuyerCompanyDirectoryImpl implements BuyerCompanyDirectory {
    private final BuyerCompanyRepository companies;
    private final OrganizationRepository organizations;
    private final LegacyCompanyDirectory legacy;

    public BuyerCompanyDirectoryImpl(
            BuyerCompanyRepository companies,
            OrganizationRepository organizations,
            LegacyCompanyDirectory legacy) {
        this.companies = companies;
        this.organizations = organizations;
        this.legacy = legacy;
    }

    public Optional<BuyerSnapshot> findById(Long companyId) {
        if (companyId == null) return Optional.empty();
        return companies
                .findById(companyId)
                .map(
                        c -> {
                            var org =
                                    organizations
                                            .findByRuc(c.getRuc())
                                            .filter(
                                                    o ->
                                                            legacy.buyerCompanyIdForOrganization(
                                                                            o.getId())
                                                                    .filter(companyId::equals)
                                                                    .isPresent())
                                            .map(o -> o.getId())
                                            .orElse(null);
                            return new BuyerSnapshot(
                                    c.getId(),
                                    c.getName(),
                                    org,
                                    c.getRuc(),
                                    c.getSector());
                        });
    }

    @Override
    public Optional<BuyerIdentity> findByRuc(String ruc) {
        return companies.findByRuc(ruc)
                .map(c -> new BuyerIdentity(c.getId(), c.getName(), c.getRuc()));
    }
}
