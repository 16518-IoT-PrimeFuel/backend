package com.primefuel.fulltank.platform.fulfillment.application.queryservices;

import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetAllDeliveriesQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveriesByProviderQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByIdQuery;

import java.util.List;
import java.util.Optional;

public interface DeliveryQueryService {
    List<Delivery> handle(GetDeliveriesByProviderQuery query);

    Optional<Delivery> handle(GetDeliveryByIdQuery query);

    List<Delivery> handle(GetAllDeliveriesQuery query);
}
