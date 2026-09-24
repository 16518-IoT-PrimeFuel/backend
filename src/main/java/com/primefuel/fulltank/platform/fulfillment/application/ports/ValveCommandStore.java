package com.primefuel.fulltank.platform.fulfillment.application.ports;

import java.time.Instant;

public interface ValveCommandStore {
    boolean exists(String commandId);

    void create(String commandId, Long deliveryId, String desiredState, Instant issuedAt);

    boolean acknowledge(String commandId, String ackId, Instant acknowledgedAt);
}
