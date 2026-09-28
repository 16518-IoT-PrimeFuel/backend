package com.primefuel.fulltank.platform.fulfillment.application.internal.listeners;

import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryBusinessJournalEntity;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DeliveryBusinessJournalRepository;
import com.primefuel.fulltank.platform.shared.events.EventEnvelope;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Synchronously journals immutable safety/valve facts in the publisher's transaction. */
@Component
public class DeliveryBusinessJournalListener {
    private final DeliveryBusinessJournalRepository journal;
    private final ObjectMapper json;

    public DeliveryBusinessJournalListener(DeliveryBusinessJournalRepository journal, ObjectMapper json) {
        this.journal = journal; this.json = json;
    }

    @EventListener
    @Transactional
    public void append(EventEnvelope event) {
        String type = switch (event.eventType()) {
            case "safety.valve.authorized.v1", "safety.valve.blocked.v1" -> "SAFETY_DECISION";
            case "valve.command.sent.v1" -> "VALVE_COMMAND";
            case "valve.state.observed.v1" -> "VALVE_OBSERVATION";
            case "safety.incident.detected.v1" -> "SAFETY_INCIDENT";
            default -> null;
        };
        if (type == null) return;
        try {
            var payload = json.readTree(event.payload());
            long deliveryId = payload.path("deliveryId").asLong();
            if (deliveryId <= 0) throw new IllegalArgumentException("deliveryId is required for journal event");
            String summary = switch (type) {
                case "SAFETY_DECISION" -> "safety.valve.authorized.v1".equals(event.eventType())
                        ? "AUTHORIZED" : "BLOCKED: " + payload.path("reason").asString("UNKNOWN");
                case "VALVE_COMMAND" -> payload.path("action").asString("OPEN");
                case "VALVE_OBSERVATION" -> payload.path("state").asString();
                default -> payload.path("kind").asString("SPONTANEOUS_OPEN");
            };
            String refId = switch (type) {
                case "SAFETY_DECISION" -> payload.path("decisionId").asString(event.aggregateId());
                case "VALVE_COMMAND" -> payload.path("commandId").asString(event.aggregateId());
                default -> payload.path("commandId").asString(event.eventId().toString());
            };
            var entry = new DeliveryBusinessJournalEntity();
            entry.setSourceEventId(event.eventId().toString()); entry.setDeliveryId(deliveryId);
            entry.setProviderId(event.organizationId()); entry.setType(type); entry.setOccurredAt(event.occurredAt());
            entry.setSummary(summary); entry.setRefId(refId);
            journal.save(entry);
        } catch (Exception e) {
            throw new IllegalStateException("Could not append immutable delivery journal event", e);
        }
    }
}
