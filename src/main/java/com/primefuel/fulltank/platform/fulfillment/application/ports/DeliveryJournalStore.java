package com.primefuel.fulltank.platform.fulfillment.application.ports;

import java.time.Instant;

public interface DeliveryJournalStore {
    boolean append(String eventId, Long deliveryId, String eventType, Instant occurredAt, String payload);
}
