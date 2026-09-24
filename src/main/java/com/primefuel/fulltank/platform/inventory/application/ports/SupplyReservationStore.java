package com.primefuel.fulltank.platform.inventory.application.ports;

public interface SupplyReservationStore {
    boolean reserve(Long requestId, Long fuelProductId, Double quantity);
}
