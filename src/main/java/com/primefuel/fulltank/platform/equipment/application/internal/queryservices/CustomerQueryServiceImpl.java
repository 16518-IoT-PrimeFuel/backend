package com.primefuel.fulltank.platform.equipment.application.internal.queryservices;

import com.primefuel.fulltank.platform.equipment.application.queryservices.CustomerQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerAccount;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerSite;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetCustomerByIdQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetCustomersByOrganizationQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetSitesByCustomerQuery;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerAccountRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerSiteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerQueryServiceImpl implements CustomerQueryService {

    private final CustomerAccountRepository customerAccountRepository;
    private final CustomerSiteRepository customerSiteRepository;

    public CustomerQueryServiceImpl(CustomerAccountRepository customerAccountRepository,
                                    CustomerSiteRepository customerSiteRepository) {
        this.customerAccountRepository = customerAccountRepository;
        this.customerSiteRepository = customerSiteRepository;
    }

    @Override
    public Optional<CustomerAccount> handle(GetCustomerByIdQuery query) {
        return customerAccountRepository.findById(query.customerAccountId());
    }

    @Override
    public List<CustomerAccount> handle(GetCustomersByOrganizationQuery query) {
        return customerAccountRepository.findByOrganizationId(query.organizationId());
    }

    @Override
    public List<CustomerSite> handle(GetSitesByCustomerQuery query) {
        return customerSiteRepository.findByCustomerAccountId(query.customerAccountId());
    }
}
