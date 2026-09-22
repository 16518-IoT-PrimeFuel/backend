package com.primefuel.fulltank.platform.equipment.domain.repositories;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerSite;

import java.util.List;
import java.util.Optional;

public interface CustomerSiteRepository {
    Optional<CustomerSite> findById(Long id);
    List<CustomerSite> findByCustomerAccountId(Long customerAccountId);
    List<CustomerSite> findByOrganizationId(Long organizationId);
    CustomerSite save(CustomerSite customerSite);
}
