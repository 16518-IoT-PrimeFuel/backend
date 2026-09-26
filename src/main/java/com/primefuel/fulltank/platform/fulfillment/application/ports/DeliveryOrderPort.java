package com.primefuel.fulltank.platform.fulfillment.application.ports;

import java.util.Optional;

public interface DeliveryOrderPort {
    Optional<DeliveryOrderData> findById(Long orderId);
    boolean dispatch(Long orderId);
    boolean receive(Long orderId);
}
