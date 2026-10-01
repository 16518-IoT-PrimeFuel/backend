package com.primefuel.fulltank.platform.ordering.interfaces.acl;

import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetAllFuelOrdersQuery;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrdersByCompanyIdQuery;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrdersByProviderIdQuery;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Superficie pública de ordering para otros contextos: solo ids y estados en tipos primitivos.
 */
@Service
public class OrderingContextFacade {

    /** Pedido reducido a lo que otros contextos necesitan; status es el nombre del estado (p. ej. "CONFIRMED"). */
    public record OrderSummary(Long id, String status) {
    }

    private final FuelOrderQueryService fuelOrderQueryService;

    public OrderingContextFacade(FuelOrderQueryService fuelOrderQueryService) {
        this.fuelOrderQueryService = fuelOrderQueryService;
    }

    public List<OrderSummary> fetchOrdersByProviderId(Long providerId) {
        return toSummaries(fuelOrderQueryService.handle(new GetFuelOrdersByProviderIdQuery(providerId)));
    }

    public List<OrderSummary> fetchOrdersByCompanyId(Long companyId) {
        return toSummaries(fuelOrderQueryService.handle(new GetFuelOrdersByCompanyIdQuery(companyId)));
    }

    public List<OrderSummary> fetchAllOrders() {
        return toSummaries(fuelOrderQueryService.handle(new GetAllFuelOrdersQuery()));
    }

    private static List<OrderSummary> toSummaries(List<FuelOrder> orders) {
        return orders.stream()
                .map(order -> new OrderSummary(order.getId(),
                        order.getStatus() != null ? order.getStatus().name() : null))
                .toList();
    }
}
