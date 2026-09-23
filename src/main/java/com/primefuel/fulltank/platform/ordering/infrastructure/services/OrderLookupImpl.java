package com.primefuel.fulltank.platform.ordering.infrastructure.services;

import com.primefuel.fulltank.platform.ordering.api.OrderLookup;
import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Adapter over {@link FuelOrderRepository} exposing the order snapshot as {@code ordering.api}. */
@Component("orderLookup")
public class OrderLookupImpl implements OrderLookup {

    private final FuelOrderRepository orderRepository;

    public OrderLookupImpl(FuelOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Optional<OrderSnapshot> findById(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orderRepository.findById(orderId).map(OrderLookupImpl::toSnapshot);
    }

    private static OrderSnapshot toSnapshot(FuelOrder order) {
        return new OrderSnapshot(order.getId(), order.getRequestId(), order.getCompanyId(),
                order.getProviderId(), order.getFuelProductId(), order.getEquipmentId(),
                order.getRequestedQuantity(), order.getTotalPrice(),
                order.getStatus() == null ? null : order.getStatus().name());
    }
}
