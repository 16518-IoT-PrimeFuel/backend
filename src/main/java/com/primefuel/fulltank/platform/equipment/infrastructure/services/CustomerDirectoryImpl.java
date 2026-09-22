package com.primefuel.fulltank.platform.equipment.infrastructure.services;

import com.primefuel.fulltank.platform.equipment.api.CustomerDirectory;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerAccountRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("customerDirectory")
public class CustomerDirectoryImpl implements CustomerDirectory {

    private final CustomerAccountRepository customerAccountRepository;

    public CustomerDirectoryImpl(CustomerAccountRepository customerAccountRepository) {
        this.customerAccountRepository = customerAccountRepository;
    }

    @Override
    public boolean ownsCustomer(Long organizationId, Long customerAccountId) {
        if (organizationId == null || customerAccountId == null) {
            return false;
        }
        return customerAccountRepository.findById(customerAccountId)
                .map(customer -> organizationId.equals(customer.getOrganizationId()))
                .orElse(false);
    }

    @Override
    public Optional<Long> customerIdForLegacyCompany(Long legacyCompanyId) {
        if (legacyCompanyId == null) {
            return Optional.empty();
        }
        return customerAccountRepository.findByLegacyCompanyId(legacyCompanyId).map(customer -> customer.getId());
    }

    @Override
    public Optional<Long> organizationIdForCustomer(Long customerAccountId) {
        if (customerAccountId == null) {
            return Optional.empty();
        }
        return customerAccountRepository.findById(customerAccountId)
                .map(customer -> customer.getOrganizationId());
    }
}
