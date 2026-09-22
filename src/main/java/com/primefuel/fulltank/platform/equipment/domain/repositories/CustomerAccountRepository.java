package com.primefuel.fulltank.platform.equipment.domain.repositories;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerAccount;

import java.util.List;
import java.util.Optional;

public interface CustomerAccountRepository {
    Optional<CustomerAccount> findById(Long id);
    List<CustomerAccount> findByOrganizationId(Long organizationId);
    Optional<CustomerAccount> findByLegacyCompanyId(Long legacyCompanyId);
    Optional<CustomerAccount> findByOrganizationIdAndRuc(Long organizationId, String ruc);
    CustomerAccount save(CustomerAccount customerAccount);
    long count();
}
