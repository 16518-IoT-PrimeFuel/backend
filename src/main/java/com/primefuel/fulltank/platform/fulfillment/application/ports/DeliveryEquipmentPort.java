package com.primefuel.fulltank.platform.fulfillment.application.ports;

public interface DeliveryEquipmentPort {
    boolean receiveFuel(Long equipmentId, Double quantity);
}
