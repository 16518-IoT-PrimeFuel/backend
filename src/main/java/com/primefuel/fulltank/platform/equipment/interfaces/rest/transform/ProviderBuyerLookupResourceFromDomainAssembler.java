package com.primefuel.fulltank.platform.equipment.interfaces.rest.transform;

import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.ProviderBuyerIdentity;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.ProviderBuyerLookupResource;

public final class ProviderBuyerLookupResourceFromDomainAssembler {
    private ProviderBuyerLookupResourceFromDomainAssembler() {}

    public static ProviderBuyerLookupResource toResource(ProviderBuyerIdentity buyer) {
        return new ProviderBuyerLookupResource(buyer.buyerCompanyId(), buyer.name(), buyer.ruc());
    }
}
