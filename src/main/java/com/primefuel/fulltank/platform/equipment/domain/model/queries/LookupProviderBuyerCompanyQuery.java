package com.primefuel.fulltank.platform.equipment.domain.model.queries;

public record LookupProviderBuyerCompanyQuery(Long providerId, String ruc) {
    public LookupProviderBuyerCompanyQuery {
        if (providerId == null || providerId <= 0) {
            throw new IllegalArgumentException("Provider id must be positive");
        }
        if (ruc == null || !ruc.matches("[0-9]{11}")) {
            throw new IllegalArgumentException("RUC must contain exactly eleven digits");
        }
    }
}
