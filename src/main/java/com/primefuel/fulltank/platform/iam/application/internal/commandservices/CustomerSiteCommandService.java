package com.primefuel.fulltank.platform.iam.application.internal.commandservices;

import com.primefuel.fulltank.platform.iam.application.ports.CustomerSiteStore;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerSiteCommandService {
    private final BuyerCompanyRepository companies;
    private final CustomerSiteStore sites;

    public CustomerSiteCommandService(BuyerCompanyRepository companies, CustomerSiteStore sites) {
        this.companies = companies;
        this.sites = sites;
    }

    @Transactional
    public Long create(Long companyId, String name, String address, String sector) {
        var company = companies.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Buyer company not found"));
        var accountId = sites.ensureAccount(companyId, company.getName());
        return sites.createSite(accountId, name, address, sector);
    }

    public boolean siteBelongsToCompany(Long siteId, Long companyId) {
        return sites.belongsToCompany(siteId, companyId);
    }
}
