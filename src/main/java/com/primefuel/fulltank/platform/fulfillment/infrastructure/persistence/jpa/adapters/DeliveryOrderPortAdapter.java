package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.fulfillment.application.ports.DeliveryOrderData;
import com.primefuel.fulltank.platform.fulfillment.application.ports.DeliveryOrderPort;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class DeliveryOrderPortAdapter implements DeliveryOrderPort {
    private final FuelOrderRepository orders;

    public DeliveryOrderPortAdapter(FuelOrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public Optional<DeliveryOrderData> findById(Long orderId) {
        return orders.findById(orderId).map(order -> new DeliveryOrderData(order.getId(), order.getRequestId(),
                order.getProviderId(), order.getFuelProductId(), order.getEquipmentId(),
                order.getRequestedQuantity(), order.getStatus().name()));
    }

    @Override
    @Transactional
    public boolean dispatch(Long orderId) {
        var order = orders.findById(orderId);
        if (order.isEmpty()) return false;
        order.get().dispatch();
        orders.save(order.get());
        return true;
    }

    @Override
    @Transactional
    public boolean receive(Long orderId) {
        var order = orders.findById(orderId);
        if (order.isEmpty()) return false;
        order.get().receive();
        orders.save(order.get());
        return true;
    }
}
