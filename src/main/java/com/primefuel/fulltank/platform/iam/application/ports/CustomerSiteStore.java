package com.primefuel.fulltank.platform.iam.application.ports;

public interface CustomerSiteStore {
    Long ensureAccount(Long legacyBuyerCompanyId, String name);

    Long createSite(Long customerAccountId, String name, String address, String sector);

    boolean belongsToCompany(Long siteId, Long legacyBuyerCompanyId);
}
