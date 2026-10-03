package com.primefuel.fulltank.platform.iam.infrastructure.services;

import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.BuyerCompany;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.MembershipRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.ProviderCompanyRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("legacyCompanyDirectory")
public class LegacyCompanyDirectoryImpl implements LegacyCompanyDirectory {

    private final BuyerCompanyRepository buyerCompanyRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final ProviderCompanyRepository providerCompanyRepository;

    public LegacyCompanyDirectoryImpl(BuyerCompanyRepository buyerCompanyRepository,
                                      OrganizationRepository organizationRepository,
                                      UserRepository userRepository,
                                      MembershipRepository membershipRepository,
                                      ProviderCompanyRepository providerCompanyRepository) {
        this.buyerCompanyRepository = buyerCompanyRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.providerCompanyRepository = providerCompanyRepository;
    }

    @Override
    public Optional<Long> organizationIdForProvider(Long providerId) {
        if (providerId == null) return Optional.empty();
        return providerCompanyRepository.findById(providerId)
                .flatMap(provider -> organizationRepository.findByRuc(provider.getRuc()))
                .filter(organization -> organization.isActive() && organization.getType() == OrganizationType.DISTRIBUTOR)
                .map(organization -> organization.getId())
                .filter(organizationId -> membershipRepository.findActiveByOrganizationId(organizationId).stream()
                        .anyMatch(membership -> userRepository.findById(membership.getUserId())
                                .filter(user -> providerId.equals(user.getProviderId())).isPresent()));
    }

    /**
     * The RUC alone is unverified, so a match only counts when the company's own user is an active member
     * of that customer organization — otherwise anyone could claim another tenant's company by RUC.
     */
    @Override
    public Optional<Long> buyerCompanyIdForOrganization(Long organizationId) {
        if (organizationId == null) {
            return Optional.empty();
        }
        return organizationRepository.findById(organizationId)
                .filter(organization -> organization.getType() == OrganizationType.CUSTOMER)
                .flatMap(organization -> buyerCompanyRepository.findByRuc(organization.getRuc()))
                .map(BuyerCompany::getId)
                .filter(companyId -> userRepository.findByCompanyId(companyId)
                        .map(user -> membershipRepository.findActiveByOrganizationId(organizationId).stream()
                                .anyMatch(membership -> user.getId().equals(membership.getUserId())))
                        .orElse(false));
    }
}
