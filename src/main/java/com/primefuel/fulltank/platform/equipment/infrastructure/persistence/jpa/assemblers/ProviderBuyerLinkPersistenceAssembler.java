package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.ProviderBuyerLink;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.ProviderBuyerLinkPersistenceEntity;

public final class ProviderBuyerLinkPersistenceAssembler {
    private ProviderBuyerLinkPersistenceAssembler() {}

    public static ProviderBuyerLink toDomainFromPersistence(
            ProviderBuyerLinkPersistenceEntity entity) {
        var link =
                new ProviderBuyerLink(
                        entity.getProviderId(),
                        entity.getBuyerCompanyId(),
                        entity.getOrganizationId());
        link.setId(entity.getId());
        return link;
    }

    public static ProviderBuyerLinkPersistenceEntity toPersistenceFromDomain(
            ProviderBuyerLink link) {
        var entity = new ProviderBuyerLinkPersistenceEntity();
        entity.setId(link.getId());
        entity.setProviderId(link.getProviderId());
        entity.setBuyerCompanyId(link.getBuyerCompanyId());
        entity.setOrganizationId(link.getOrganizationId());
        return entity;
    }
}
