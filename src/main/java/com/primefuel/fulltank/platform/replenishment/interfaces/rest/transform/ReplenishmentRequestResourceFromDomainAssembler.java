package com.primefuel.fulltank.platform.replenishment.interfaces.rest.transform;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.ReplenishmentRequestResource;

public final class ReplenishmentRequestResourceFromDomainAssembler {

    private ReplenishmentRequestResourceFromDomainAssembler() {
    }

    public static ReplenishmentRequestResource toResourceFromDomain(ReplenishmentRequest request) {
        return new ReplenishmentRequestResource(
                request.getId(),
                request.getOrganizationId(),
                request.getCustomerAccountId(),
                request.getTankId(),
                request.getProviderId(),
                request.getFuelProductId(),
                request.getQuantity(),
                request.getUnit(),
                request.getUnitPrice(),
                request.getStatus() == null ? null : request.getStatus().name(),
                request.getSource() == null ? null : request.getSource().name(),
                request.getRejectionReason(),
                request.getOrderId(),
                request.getVersion());
    }
}
