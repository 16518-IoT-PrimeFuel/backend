package com.primefuel.fulltank.platform.ordering.interfaces.rest.transform;

import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.FuelOrderResource;

public final class FuelOrderResourceFromEntityAssembler {

    private FuelOrderResourceFromEntityAssembler() {
    }

    public static FuelOrderResource toResourceFromEntity(FuelOrder order) {
        return toResourceFromEntity(order, order.getRequestId());
    }

    public static FuelOrderResource toResourceFromEntity(FuelOrder order, Long requestId) {
        return new FuelOrderResource(order.getId(), requestId, order.getCompanyId(), order.getProviderId(),
                order.getFuelProductId(), order.getEquipmentId(), order.getRequestedQuantity(),
                order.getTotalPrice(), order.getStatus(), order.getDeliveryAddress(),
                order.getScheduledDate());
    }
}
