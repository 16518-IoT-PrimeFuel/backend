package com.primefuel.fulltank.platform.fulfillment.application.ports;

import java.time.Instant;

public record JournalEntryData(String eventId, Long deliveryId, String eventType,
                               Instant occurredAt, String payload) {
}
