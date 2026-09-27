package com.primefuel.fulltank.platform.equipment.infrastructure.services;

import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import com.primefuel.fulltank.platform.equipment.api.CustomerDirectory;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerAccountRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("customerDirectory")
public class CustomerDirectoryImpl implements CustomerDirectory {

    private final CustomerAccountRepository customerAccountRepository;
    private final LegacyCompanyDirectory legacyCompanyDirectory;

    public CustomerDirectoryImpl(CustomerAccountRepository customerAccountRepository,
                                 LegacyCompanyDirectory legacyCompanyDirectory) {
        this.customerAccountRepository = customerAccountRepository;
        this.legacyCompanyDirectory = legacyCompanyDirectory;
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
    public Optional<Long> legacyCompanyIdForCustomer(Long customerAccountId) {
        // An account created through the API carries no explicit mapping: fall back to its organization's buyer company.
        return customerAccountId == null ? Optional.empty()
                : customerAccountRepository.findById(customerAccountId)
                .flatMap(customer -> customer.getLegacyCompanyId() != null
                        ? Optional.of(customer.getLegacyCompanyId())
                        : legacyCompanyDirectory.buyerCompanyIdForOrganization(customer.getOrganizationId()));
    }
}
