package com.primefuel.fulltank.platform.iam.infrastructure.services;

import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.BuyerCompany;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.MembershipRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("legacyCompanyDirectory")
public class LegacyCompanyDirectoryImpl implements LegacyCompanyDirectory {

    private final BuyerCompanyRepository buyerCompanyRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    public LegacyCompanyDirectoryImpl(BuyerCompanyRepository buyerCompanyRepository,
                                      OrganizationRepository organizationRepository,
                                      UserRepository userRepository,
                                      MembershipRepository membershipRepository) {
        this.buyerCompanyRepository = buyerCompanyRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
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
