package com.primefuel.fulltank.platform.safety.valve.application;

import tools.jackson.databind.ObjectMapper;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.ValveCommandEntity;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.ValveCommandRepository;
import com.primefuel.fulltank.platform.shared.events.EventEnvelope;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class ValveCommandIssuer {
    private final ValveCommandRepository commands;
    private final EventPublicationRegistry events;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean enabled;
    private final String secret;

    public ValveCommandIssuer(ValveCommandRepository commands, EventPublicationRegistry events,
            ObjectMapper json, Clock clock,
            @Value("${safety.valve.commands.enabled:false}") boolean enabled,
            @Value("${safety.valve.signing-secret:}") String secret) {
        this.commands = commands; this.events = events; this.json = json; this.clock = clock;
        this.enabled = enabled; this.secret = secret;
    }

    @EventListener
    @Transactional
    public void onEnvelope(EventEnvelope envelope) {
        if ("safety.valve.authorized.v1".equals(envelope.eventType()) && enabled) {
            issue(envelope);
        } else if ("delivery.failed.v1".equals(envelope.eventType())) {
            revokeFailedDelivery(envelope);
        }
    }

    private void issue(EventEnvelope envelope) {
        try {
            var data = json.readTree(envelope.payload());
            long deliveryId = data.path("deliveryId").asLong();
            long decisionId = data.path("decisionId").asLong();
            if (deliveryId <= 0 || decisionId <= 0 || commands.existsByDeliveryIdAndDecisionId(deliveryId, decisionId)) return;
            if (secret.isBlank()) throw new IllegalStateException("safety.valve.signing-secret is required when commands are enabled");
            Instant issuedAt = clock.instant();
            Instant expiresAt = issuedAt.plus(Duration.ofSeconds(120));
            String commandId = UUID.randomUUID().toString(), nonce = UUID.randomUUID().toString();
            String signature = sign(commandId, deliveryId, "OPEN", nonce, expiresAt);
            var command = new ValveCommandEntity();
            command.setCommandId(commandId); command.setDeliveryId(deliveryId); command.setProviderId(envelope.organizationId());
            command.setDecisionId(decisionId); command.setAction("OPEN"); command.setNonce(nonce);
            command.setIssuedAt(issuedAt); command.setExpiresAt(expiresAt); command.setSignature(signature);
            command.setProtocolVersion("v1"); command.setStatus("PENDING");
            commands.save(command);
            String payload = json.writeValueAsString(java.util.Map.of("commandId", commandId, "deliveryId", deliveryId,
                    "providerId", envelope.organizationId(), "decisionId", decisionId, "action", "OPEN",
                    "nonce", nonce, "issuedAt", issuedAt.toString(), "expiresAt", expiresAt.toString(),
                    "signature", signature, "protocolVersion", "v1"));
            events.publish("valve.operation.requested.v1", "ValveCommand", commandId, envelope.organizationId(), 1L, payload);
            events.publish("valve.command.sent.v1", "ValveCommand", commandId, envelope.organizationId(), 1L, payload);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Could not create signed valve command", e);
        }
    }

    private void revokeFailedDelivery(EventEnvelope envelope) {
        try {
            var payload = json.readTree(envelope.payload());
            if (!"FAILED".equals(payload.path("terminalState").asText()) && !"CANCELLED".equals(payload.path("terminalState").asText())) return;
            long deliveryId = payload.path("deliveryId").asLong();
            commands.findByDeliveryIdAndStatus(deliveryId, "PENDING").forEach(command -> command.setStatus("REVOKED"));
        } catch (Exception e) { throw new IllegalStateException("Could not revoke valve command", e); }
    }

    private String sign(String commandId, long deliveryId, String action, String nonce, Instant expiresAt) throws Exception {
        String canonical = "v1|%s|%d|%s|%s|%s".formatted(commandId, deliveryId, action, nonce, expiresAt);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
