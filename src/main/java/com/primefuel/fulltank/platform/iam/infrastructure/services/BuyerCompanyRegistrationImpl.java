package com.primefuel.fulltank.platform.iam.infrastructure.services;

import com.primefuel.fulltank.platform.iam.api.BuyerCompanyDirectory.BuyerSnapshot;
import com.primefuel.fulltank.platform.iam.api.BuyerCompanyRegistration;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.BuyerCompany;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateBuyerCompanyCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationRepository;
import com.primefuel.fulltank.platform.shared.application.result.*;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class BuyerCompanyRegistrationImpl implements BuyerCompanyRegistration {
    private final BuyerCompanyRepository companies;
    private final OrganizationRepository organizations;

    public BuyerCompanyRegistrationImpl(
            BuyerCompanyRepository companies, OrganizationRepository organizations) {
        this.companies = companies;
        this.organizations = organizations;
    }

    public Result<BuyerSnapshot, ApplicationError> register(Registration registration) {
        if (registration.buyerCompanyId() != null) {
            var buyer = companies.findById(registration.buyerCompanyId()).orElse(null);
            if (buyer == null)
                return Result.failure(
                        ApplicationError.notFound(
                                "BuyerCompany", String.valueOf(registration.buyerCompanyId())));
            var organization =
                    organizations
                            .findByRuc(buyer.getRuc())
                            .filter(o -> o.isActive() && o.getType() == OrganizationType.CUSTOMER)
                            .orElse(null);
            if (organization == null)
                return Result.failure(
                        ApplicationError.notFound(
                                "BuyerCompany", String.valueOf(registration.buyerCompanyId())));
            return Result.success(
                    new BuyerSnapshot(
                            buyer.getId(),
                            buyer.getName(),
                            organization.getId(),
                            buyer.getRuc(),
                            buyer.getSector()));
        }
        if (registration.name() == null
                || registration.name().isBlank()
                || registration.ruc() == null
                || !registration.ruc().matches("[0-9]{11}")) {
            return Result.failure(
                    ApplicationError.validationError(
                            "buyer",
                            "A name and an eleven-digit RUC are required for a new buyer"));
        }
        if (companies.existsByRuc(registration.ruc())
                || organizations.findByRuc(registration.ruc()).isPresent()) {
            return Result.failure(
                    ApplicationError.conflict(
                            "BuyerCompany",
                            "RUC already registered; explicitly link the existing buyerCompanyId"));
        }
        var buyer =
                companies.save(
                        new BuyerCompany(
                                new CreateBuyerCompanyCommand(
                                        registration.name(),
                                        registration.ruc(),
                                        registration.sector(),
                                        registration.address(),
                                        registration.contactEmail(),
                                        registration.phone())));
        var organization =
                organizations.save(
                        new Organization(
                                new CreateOrganizationCommand(
                                        registration.name(),
                                        registration.ruc(),
                                        OrganizationType.CUSTOMER)));
        return Result.success(
                new BuyerSnapshot(
                        buyer.getId(),
                        buyer.getName(),
                        organization.getId(),
                        buyer.getRuc(),
                        buyer.getSector()));
    }
}
