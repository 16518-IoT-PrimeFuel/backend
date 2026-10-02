package com.primefuel.fulltank.platform.analytics.application.internal.outboundservices.acl;

import com.primefuel.fulltank.platform.inventory.api.FuelProductLookup;
import com.primefuel.fulltank.platform.ordering.interfaces.acl.OrderingContextFacade;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** ACL de analytics hacia ordering: traduce la facade a tipos propios de analytics. */
@Service
public class ExternalOrderingService {

    public record OrderData(
            Long id, String status, Double quantityLitres, LocalDateTime createdAt) {
        public OrderData(Long id, String status) {
            this(id, status, null, null);
        }
    }

    private final OrderingContextFacade orderingContextFacade;
    private final ReplenishmentLookup requests;
    private final FuelProductLookup products;

    public ExternalOrderingService(
            OrderingContextFacade orderingContextFacade,
            ReplenishmentLookup requests,
            FuelProductLookup products) {
        this.orderingContextFacade = orderingContextFacade;
        this.requests = requests;
        this.products = products;
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

    private List<OrderData> toData(List<OrderingContextFacade.OrderSummary> orders) {
        return orders.stream()
                .map(
                        order -> {
                            var request =
                                    requests.findByOrderId(order.id())
                                            .filter(
                                                    r ->
                                                            Objects.equals(
                                                                    order.providerId(),
                                                                    r.providerId()));
                            var unit =
                                    request.map(r -> r.unit())
                                            .orElseGet(
                                                    () ->
                                                            products.findById(order.fuelProductId())
                                                                    .filter(
                                                                            p ->
                                                                                    Objects.equals(
                                                                                            order
                                                                                                    .providerId(),
                                                                                            p
                                                                                                    .providerId()))
                                                                    .map(p -> p.unit())
                                                                    .orElse(null));
                            Double litres =
                                    request.map(r -> Unit.fromCode(r.unit()).toLitres(r.quantity()))
                                            .orElseGet(
                                                    () ->
                                                            order.quantity() == null || unit == null
                                                                    ? null
                                                                    : Unit.fromCode(unit)
                                                                            .toLitres(
                                                                                    order
                                                                                            .quantity()));
                            return new OrderData(
                                    order.id(), order.status(), litres, order.createdAt());
                        })
                .toList();
    }
}
