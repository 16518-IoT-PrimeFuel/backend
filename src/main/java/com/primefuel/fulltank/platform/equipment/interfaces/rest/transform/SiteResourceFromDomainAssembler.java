package com.primefuel.fulltank.platform.equipment.interfaces.rest.transform;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerSite;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.SiteResource;

public final class SiteResourceFromDomainAssembler {

    private SiteResourceFromDomainAssembler() {
    }

    public static SiteResource toResourceFromDomain(CustomerSite site) {
        return new SiteResource(
                site.getId(),
                site.getOrganizationId(),
                site.getCustomerAccountId(),
                site.getName(),
                site.getAddress(),
                site.isActive());
    }
}
