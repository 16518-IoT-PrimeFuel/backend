package com.primefuel.fulltank.platform.equipment.interfaces.rest.transform;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerAccount;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.CustomerResource;

public final class CustomerResourceFromDomainAssembler {

    private CustomerResourceFromDomainAssembler() {
    }

    public static CustomerResource toResourceFromDomain(CustomerAccount customer) {
        return new CustomerResource(
                customer.getId(),
                customer.getOrganizationId(),
                customer.getName(),
                customer.getRuc(),
                customer.getAddress(),
                customer.getContactEmail(),
                customer.getPhone(),
                customer.getLegacyCompanyId(),
                customer.isActive());
    }
}
