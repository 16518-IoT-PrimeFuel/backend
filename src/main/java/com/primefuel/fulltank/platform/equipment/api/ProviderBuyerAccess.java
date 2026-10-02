package com.primefuel.fulltank.platform.equipment.api;

import java.util.Optional;
import java.util.Set;

/** Provider access through explicit buyer links, orders, or replenishment requests. */
public interface ProviderBuyerAccess {
    boolean canReadTank(Long providerId, Long tankId);

    Set<Long> linkedOrganizations(Long providerId);

    Optional<Long> linkedBuyerOrganization(Long providerId, Long buyerCompanyId);
}
