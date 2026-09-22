package com.primefuel.fulltank.platform.shared.events;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Long organizationId,
        Long aggregateVersion,
        Instant occurredAt,
        String payload) {
}
