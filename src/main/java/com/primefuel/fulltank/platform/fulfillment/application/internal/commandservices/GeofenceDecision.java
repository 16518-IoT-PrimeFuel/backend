package com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices;

public record GeofenceDecision(boolean inside, int version, double distanceMeters, String reason) {
    public GeofenceDecision(boolean inside, int version, double distanceMeters) {
        this(inside, version, distanceMeters, inside ? "INSIDE" : "OUTSIDE");
    }
}
