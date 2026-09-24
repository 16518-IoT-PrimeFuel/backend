package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.iam.application.ports.CustomerSiteStore;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.CustomerAccountPersistenceEntity;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.CustomerSitePersistenceEntity;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.CustomerAccountJpaRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.CustomerSiteJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class CustomerSiteStoreAdapter implements CustomerSiteStore {
    private final CustomerAccountJpaRepository accounts;
    private final CustomerSiteJpaRepository sites;

    public CustomerSiteStoreAdapter(CustomerAccountJpaRepository accounts, CustomerSiteJpaRepository sites) {
        this.accounts = accounts;
        this.sites = sites;
    }

    @Override
    public Long ensureAccount(Long legacyBuyerCompanyId, String name) {
        return accounts.findByLegacyBuyerCompanyId(legacyBuyerCompanyId).map(CustomerAccountPersistenceEntity::getId)
                .orElseGet(() -> {
                    var account = new CustomerAccountPersistenceEntity();
                    account.setLegacyBuyerCompanyId(legacyBuyerCompanyId);
                    account.setName(name);
                    account.setStatus("ACTIVE");
                    return accounts.save(account).getId();
                });
    }

    @Override
    public Long createSite(Long customerAccountId, String name, String address, String sector) {
        var site = new CustomerSitePersistenceEntity();
        site.setCustomerAccountId(customerAccountId);
        site.setName(name);
        site.setAddress(address);
        site.setSector(sector);
        site.setStatus("ACTIVE");
        return sites.save(site).getId();
    }

    @Override
    public boolean belongsToCompany(Long siteId, Long legacyBuyerCompanyId) {
        return sites.findById(siteId).flatMap(site -> accounts.findById(site.getCustomerAccountId()))
                .map(account -> legacyBuyerCompanyId.equals(account.getLegacyBuyerCompanyId())).orElse(false);
    }
}
