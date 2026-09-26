package com.primefuel.fulltank.platform.fulfillment.application.ports;

public record DeliveryOrderData(Long id, Long requestId, Long providerId, Long fuelProductId,
                                Long equipmentId, Double requestedQuantity, String status) {
}
