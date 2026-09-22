package com.primefuel.fulltank.platform.iam.infrastructure.services;

import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component("legacyCompanyDirectory")
public class LegacyCompanyDirectoryImpl implements LegacyCompanyDirectory {

    private final BuyerCompanyRepository buyerCompanyRepository;
    private final OrganizationRepository organizationRepository;

    public LegacyCompanyDirectoryImpl(BuyerCompanyRepository buyerCompanyRepository,
                                      OrganizationRepository organizationRepository) {
        this.buyerCompanyRepository = buyerCompanyRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public List<LegacyCompanySnapshot> findAllBuyerCompanies() {
        return buyerCompanyRepository.findAll().stream()
                .map(company -> new LegacyCompanySnapshot(
                        company.getId(), company.getName(), company.getRuc(),
                        company.getAddress(), company.getContactEmail(), company.getPhone()))
                .toList();
    }

    @Override
    public Optional<Long> organizationIdForRuc(String ruc) {
        if (ruc == null || ruc.isBlank()) {
            return Optional.empty();
        }
        return organizationRepository.findByRuc(ruc).map(Organization::getId);
    }
}
