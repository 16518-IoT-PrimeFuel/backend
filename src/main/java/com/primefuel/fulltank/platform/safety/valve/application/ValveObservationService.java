package com.primefuel.fulltank.platform.safety.valve.application;

import tools.jackson.databind.ObjectMapper;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.SafetyIncidentEntity;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.SafetyIncidentRepository;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.ValveCommandEntity;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.ValveCommandRepository;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
public class ValveObservationService {
    private final ValveCommandRepository commands;
    private final SafetyIncidentRepository incidents;
    private final EventPublicationRegistry events;
    private final ObjectMapper json;
    private final Clock clock;

    public ValveObservationService(ValveCommandRepository commands, SafetyIncidentRepository incidents,
            EventPublicationRegistry events, ObjectMapper json, Clock clock) {
        this.commands = commands; this.incidents = incidents; this.events = events; this.json = json; this.clock = clock;
    }

    @Transactional
    public boolean observe(Long deliveryId, Long providerId, String state, Instant observedAt, String commandId) {
        if (!"OPEN".equals(state) && !"CLOSED".equals(state)) throw new IllegalArgumentException("state must be OPEN or CLOSED");
        if ("CLOSED".equals(state)) {
            publishObserved(deliveryId, providerId, state, observedAt, commandId);
            return false;
        }
        Instant now = clock.instant();
        var pending = commands.findByDeliveryIdAndStatus(deliveryId, "PENDING");
        // ponytail: lazy expiry during reconciliation; add a scheduler only if proactive expiry is required.
        pending.stream().filter(c -> c.getExpiresAt().isBefore(now)).forEach(c -> c.setStatus("EXPIRED"));
        ValveCommandEntity match = null;
        if (commandId != null && !commandId.isBlank()) {
            match = commands.findByDeliveryIdAndCommandId(deliveryId, commandId).orElse(null);
            if (match != null && "ACKED".equals(match.getStatus())) return false;
            if (match == null || !providerId.equals(match.getProviderId()) || !"PENDING".equals(match.getStatus())
                    || match.getExpiresAt().isBefore(now)) match = null;
        } else {
            var valid = pending.stream().filter(c -> providerId.equals(c.getProviderId())
                    && "PENDING".equals(c.getStatus()) && !c.getExpiresAt().isBefore(now)).toList();
            if (valid.size() == 1) match = valid.getFirst();
        }
        if (match == null) {
            recordIncident(deliveryId, providerId, observedAt, commandId);
            events.publish("safety.incident.detected.v1", "Delivery", String.valueOf(deliveryId), providerId, 1L,
                    payload(Map.of("deliveryId", deliveryId, "providerId", providerId, "kind", "SPONTANEOUS_OPEN",
                            "commandId", commandId == null ? "" : commandId, "occurredAt", observedAt.toString())));
            return true;
        }
        match.setStatus("ACKED"); match.setAckedAt(now); commands.save(match);
        publishObserved(deliveryId, providerId, state, observedAt, match.getCommandId());
        return false;
    }

    private void publishObserved(Long deliveryId, Long providerId, String state, Instant observedAt, String commandId) {
        events.publish("valve.state.observed.v1", "Delivery", String.valueOf(deliveryId), providerId, 1L,
                payload(Map.of("deliveryId", deliveryId, "providerId", providerId, "state", state,
                        "observedAt", observedAt.toString(), "commandId", commandId == null ? "" : commandId)));
    }

    private void recordIncident(Long deliveryId, Long providerId, Instant occurredAt, String commandId) {
        var incident = new SafetyIncidentEntity(); incident.setDeliveryId(deliveryId); incident.setProviderId(providerId);
        incident.setKind("SPONTANEOUS_OPEN"); incident.setCommandId(commandId); incident.setOccurredAt(occurredAt);
        incident.setRecordedAt(clock.instant()); incidents.save(incident);
    }

    private String payload(Map<String, ?> value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Could not serialize valve event", e); }
    }
}
