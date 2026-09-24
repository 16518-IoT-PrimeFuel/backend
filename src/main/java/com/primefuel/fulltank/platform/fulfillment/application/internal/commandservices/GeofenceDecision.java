package com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices;

public record GeofenceDecision(boolean inside, int version, double distanceMeters) {
}
