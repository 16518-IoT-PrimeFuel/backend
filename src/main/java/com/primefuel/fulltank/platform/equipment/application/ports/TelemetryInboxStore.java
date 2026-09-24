package com.primefuel.fulltank.platform.equipment.application.ports;

import java.time.Instant;

public interface TelemetryInboxStore {
    boolean claim(String eventId, String deviceId, String channel, long sequenceNumber, Instant receivedAt);

    void complete(String eventId, String status, String error);

    void advanceCheckpoint(String deviceId, String channel, long sequenceNumber);
}
