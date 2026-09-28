package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerAccount;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerSite;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterSiteCommand;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerAccountRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerSiteRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerCommandServiceImpl implements CustomerCommandService {

    private final CustomerAccountRepository customerAccountRepository;
    private final CustomerSiteRepository customerSiteRepository;

    public CustomerCommandServiceImpl(CustomerAccountRepository customerAccountRepository,
                                      CustomerSiteRepository customerSiteRepository) {
        this.customerAccountRepository = customerAccountRepository;
        this.customerSiteRepository = customerSiteRepository;
    }

    @Override
    @Transactional
    public Result<CustomerAccount, ApplicationError> handle(RegisterCustomerCommand command) {
        if (command.organizationId() == null) {
            return Result.failure(ApplicationError.validationError("organization", "An organization is required"));
        }
        if (command.ruc() != null && customerAccountRepository
                .findByOrganizationIdAndRuc(command.organizationId(), command.ruc()).isPresent()) {
            return Result.failure(ApplicationError.conflict("CustomerAccount", "RUC already registered for this organization"));
        }
        try {
            return Result.success(customerAccountRepository.save(new CustomerAccount(command)));
        } catch (DataIntegrityViolationException exception) {
            return Result.failure(ApplicationError.conflict("CustomerAccount", "Customer already registered"));
        }
    }

    @Override
    @Transactional
    public Result<CustomerSite, ApplicationError> handle(RegisterSiteCommand command) {
        var customer = customerAccountRepository.findById(command.customerAccountId());
        if (customer.isEmpty()) {
            return Result.failure(ApplicationError.notFound("CustomerAccount", command.customerAccountId().toString()));
        }
        if (!customer.get().getOrganizationId().equals(command.organizationId())) {
            return Result.failure(ApplicationError.forbidden("The site must belong to the same organization as the customer"));
        }
        return Result.success(customerSiteRepository.save(new CustomerSite(command)));
    }
}
