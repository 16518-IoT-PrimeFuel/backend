package com.primefuel.fulltank.platform.shared.application.events;

import java.time.Instant;

public record DurableEvent(
        String eventKey,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload,
        Instant occurredAt) {
}
