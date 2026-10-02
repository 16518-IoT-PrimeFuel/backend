package com.primefuel.fulltank.platform.equipment.domain.model.aggregates;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProviderBuyerLink {
    private Long id;
    private Long providerId;
    private Long buyerCompanyId;
    private Long organizationId;

    public ProviderBuyerLink(Long providerId, Long buyerCompanyId, Long organizationId) {
        this.providerId = providerId;
        this.buyerCompanyId = buyerCompanyId;
        this.organizationId = organizationId;
    }
}
