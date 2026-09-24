package com.primefuel.fulltank.platform.equipment.application.ports;

public record RefillPolicyData(Long tankId, Long buyerCompanyId, Long providerId, Long fuelProductId,
                               double thresholdValue, double hysteresisValue, double targetVolume,
                               String deliveryAddress, boolean enabled) {
}
