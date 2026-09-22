package com.primefuel.fulltank.platform.ordering.application.queryservices;

import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetAllFuelOrdersQuery;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrderByIdQuery;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrdersByCompanyIdQuery;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrdersByProviderIdQuery;

import java.util.List;
import java.util.Optional;

public interface FuelOrderQueryService {
    Optional<FuelOrder> handle(GetFuelOrderByIdQuery query);
    List<FuelOrder> handle(GetAllFuelOrdersQuery query);
    List<FuelOrder> handle(GetFuelOrdersByCompanyIdQuery query);
    List<FuelOrder> handle(GetFuelOrdersByProviderIdQuery query);

    /**
     * The requested quantity of an order, without exposing the {@link FuelOrder} aggregate. Added for
     * S14/T14-A: the delivery physical close needs the requested volume as evidence context, and other
     * modules must not reach into {@code ordering.domain..}.
     */
    Optional<Double> findRequestedQuantity(Long orderId);
}
