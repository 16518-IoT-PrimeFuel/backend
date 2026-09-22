package com.primefuel.fulltank.platform.equipment.application.queryservices;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerAccount;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerSite;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetCustomerByIdQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetCustomersByOrganizationQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetSitesByCustomerQuery;

import java.util.List;
import java.util.Optional;

public interface CustomerQueryService {
    Optional<CustomerAccount> handle(GetCustomerByIdQuery query);
    List<CustomerAccount> handle(GetCustomersByOrganizationQuery query);
    List<CustomerSite> handle(GetSitesByCustomerQuery query);
}
