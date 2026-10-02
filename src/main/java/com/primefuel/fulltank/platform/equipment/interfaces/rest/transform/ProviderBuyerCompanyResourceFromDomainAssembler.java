package com.primefuel.fulltank.platform.equipment.interfaces.rest.transform;

import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.ProviderBuyerCompany;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.ProviderBuyerCompanyResource;

public final class ProviderBuyerCompanyResourceFromDomainAssembler {
    private ProviderBuyerCompanyResourceFromDomainAssembler() {}

    public static ProviderBuyerCompanyResource toResourceFromDomain(ProviderBuyerCompany c) {
        return new ProviderBuyerCompanyResource(
                c.id(),
                c.name(),
                c.organizationId(),
                c.tankCount(),
                c.criticalTankCount(),
                c.activeOrderCount(),
                c.historicalOrderCount(),
                c.sites().stream()
                        .map(
                                s ->
                                        new ProviderBuyerCompanyResource.SiteResource(
                                                s.id(),
                                                s.customerAccountId(),
                                                s.name(),
                                                s.address()))
                        .toList());
    }
}
