package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.ProviderBuyerCommandService;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.ProviderBuyerLink;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.*;
import com.primefuel.fulltank.platform.equipment.domain.repositories.*;
import com.primefuel.fulltank.platform.iam.api.BuyerCompanyRegistration;
import com.primefuel.fulltank.platform.shared.application.result.*;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Service
@Transactional
public class ProviderBuyerCommandServiceImpl implements ProviderBuyerCommandService {
    private final BuyerCompanyRegistration registration;
    private final ProviderBuyerLinkRepository links;
    private final CustomerAccountRepository accounts;
    private final CustomerSiteRepository sites;
    private final CustomerCommandService customers;

    public ProviderBuyerCommandServiceImpl(
            BuyerCompanyRegistration registration,
            ProviderBuyerLinkRepository links,
            CustomerAccountRepository accounts,
            CustomerSiteRepository sites,
            CustomerCommandService customers) {
        this.registration = registration;
        this.links = links;
        this.accounts = accounts;
        this.sites = sites;
        this.customers = customers;
    }

    public Result<Long, ApplicationError> handle(RegisterProviderBuyerCommand command) {
        if (command.buyerCompanyId() != null
                && links.findByProviderIdAndBuyerCompanyId(
                                command.providerId(), command.buyerCompanyId())
                        .isPresent()) {
            return Result.failure(
                    ApplicationError.conflict(
                            "ProviderBuyerLink", "Buyer already linked to this provider"));
        }
        var registered =
                registration.register(
                        new BuyerCompanyRegistration.Registration(
                                command.buyerCompanyId(),
                                command.name(),
                                command.ruc(),
                                command.sector(),
                                command.address(),
                                command.contactEmail(),
                                command.phone()));
        if (registered instanceof Result.Failure<?, ?> failed)
            return Result.failure((ApplicationError) failed.error());
        var buyer = registered.getOrElse(null);
        var account =
                accounts.findByOrganizationId(buyer.organizationId()).stream()
                        .filter(a -> a.isActive())
                        .findFirst()
                        .orElse(null);
        if (account == null) {
            var created =
                    customers.handle(
                            new RegisterCustomerCommand(
                                    buyer.organizationId(),
                                    buyer.name(),
                                    command.ruc(),
                                    command.address(),
                                    command.contactEmail(),
                                    command.phone(),
                                    buyer.id()));
            if (created instanceof Result.Failure<?, ?> failed)
                return rollback((ApplicationError) failed.error());
            account = created.getOrElse(null);
        }
        if (sites.findByOrganizationId(buyer.organizationId()).stream()
                .noneMatch(s -> s.isActive())) {
            var created =
                    customers.handle(
                            new RegisterSiteCommand(
                                    buyer.organizationId(),
                                    account.getId(),
                                    command.siteName() == null ? buyer.name() : command.siteName(),
                                    command.address()));
            if (created instanceof Result.Failure<?, ?> failed)
                return rollback((ApplicationError) failed.error());
        }
        links.save(new ProviderBuyerLink(command.providerId(), buyer.id(), buyer.organizationId()));
        return Result.success(buyer.id());
    }

    private static Result<Long, ApplicationError> rollback(ApplicationError error) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return Result.failure(error);
    }
}
