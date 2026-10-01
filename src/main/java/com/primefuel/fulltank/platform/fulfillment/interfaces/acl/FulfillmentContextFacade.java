package com.primefuel.fulltank.platform.fulfillment.interfaces.acl;

import com.primefuel.fulltank.platform.fulfillment.application.queryservices.DeliveryQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetAllDeliveriesQuery;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Superficie pública de fulfillment para otros contextos: estados de entrega como texto.
 */
@Service
public class FulfillmentContextFacade {

    private final DeliveryQueryService deliveryQueryService;

    public FulfillmentContextFacade(DeliveryQueryService deliveryQueryService) {
        this.deliveryQueryService = deliveryQueryService;
    }

    /** Estado de cada entrega (nombre del enum, p. ej. "DELIVERED"). */
    public List<String> fetchAllDeliveryStatuses() {
        return deliveryQueryService.handle(new GetAllDeliveriesQuery()).stream()
                .map(delivery -> delivery.getStatus() != null ? delivery.getStatus().name() : null)
                .toList();
    }
}
