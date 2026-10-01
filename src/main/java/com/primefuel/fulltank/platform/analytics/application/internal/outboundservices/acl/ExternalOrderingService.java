package com.primefuel.fulltank.platform.analytics.application.internal.outboundservices.acl;

import com.primefuel.fulltank.platform.ordering.interfaces.acl.OrderingContextFacade;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ACL de analytics hacia ordering: traduce la facade a tipos propios de analytics.
 */
@Service
public class ExternalOrderingService {

    public record OrderData(Long id, String status) {
    }

    private final OrderingContextFacade orderingContextFacade;

    public ExternalOrderingService(OrderingContextFacade orderingContextFacade) {
        this.orderingContextFacade = orderingContextFacade;
    }

    public List<OrderData> fetchOrdersByProviderId(Long providerId) {
        return toData(orderingContextFacade.fetchOrdersByProviderId(providerId));
    }

    public List<OrderData> fetchOrdersByCompanyId(Long companyId) {
        return toData(orderingContextFacade.fetchOrdersByCompanyId(companyId));
    }

    public List<OrderData> fetchAllOrders() {
        return toData(orderingContextFacade.fetchAllOrders());
    }

    private static List<OrderData> toData(List<OrderingContextFacade.OrderSummary> orders) {
        return orders.stream().map(order -> new OrderData(order.id(), order.status())).toList();
    }
}
