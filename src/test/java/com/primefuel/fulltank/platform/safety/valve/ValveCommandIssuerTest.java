package com.primefuel.fulltank.platform.safety.valve;

import tools.jackson.databind.ObjectMapper;
import com.primefuel.fulltank.platform.safety.valve.application.ValveCommandIssuer;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.ValveCommandEntity;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.ValveCommandRepository;
import com.primefuel.fulltank.platform.shared.events.EventEnvelope;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ValveCommandIssuerTest {
    private static final String SECRET = "test-valve-secret";
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Test void authorizedEventIssuesSignedOpenCommandAndTwoEvents() throws Exception {
        var repo = mock(ValveCommandRepository.class);
        var events = mock(EventPublicationRegistry.class);
        when(repo.existsByDeliveryIdAndDecisionId(21L, 34L)).thenReturn(false);
        var issuer = new ValveCommandIssuer(repo, events, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), true, SECRET);
        issuer.onEnvelope(envelope("safety.valve.authorized.v1", 7L,
                "{\"deliveryId\":21,\"decisionId\":34}"));
        var captor = org.mockito.ArgumentCaptor.forClass(ValveCommandEntity.class);
        verify(repo).save(captor.capture());
        var command = captor.getValue();
        assertEquals(21L, command.getDeliveryId()); assertEquals(7L, command.getProviderId());
        assertEquals(34L, command.getDecisionId()); assertEquals("OPEN", command.getAction());
        assertEquals("PENDING", command.getStatus()); assertEquals("v1", command.getProtocolVersion());
        assertEquals(NOW.plusSeconds(120), command.getExpiresAt());
        assertEquals(command.getSignature(), signature(command));
        verify(events).publish(eq("valve.operation.requested.v1"), eq("ValveCommand"), eq(command.getCommandId()), eq(7L), eq(1L), anyString());
        verify(events).publish(eq("valve.command.sent.v1"), eq("ValveCommand"), eq(command.getCommandId()), eq(7L), eq(1L), anyString());
    }

    @Test void disabledFlagCreatesNoCommand() {
        var repo = mock(ValveCommandRepository.class); var events = mock(EventPublicationRegistry.class);
        new ValveCommandIssuer(repo, events, new ObjectMapper(), Clock.systemUTC(), false, "")
                .onEnvelope(envelope("safety.valve.authorized.v1", 7L, "{\"deliveryId\":21,\"decisionId\":34}"));
        verifyNoInteractions(repo, events);
    }

    @Test void blockedDecisionCreatesNoCommand() {
        var repo = mock(ValveCommandRepository.class); var events = mock(EventPublicationRegistry.class);
        new ValveCommandIssuer(repo, events, new ObjectMapper(), Clock.systemUTC(), true, SECRET)
                .onEnvelope(envelope("safety.valve.blocked.v1", 7L, "{\"deliveryId\":21,\"decisionId\":34}"));
        verifyNoInteractions(repo, events);
    }

    @Test void failedDeliveryRevokesPendingCommands() {
        var repo = mock(ValveCommandRepository.class); var events = mock(EventPublicationRegistry.class);
        var pending = new ValveCommandEntity(); pending.setStatus("PENDING");
        when(repo.findByDeliveryIdAndStatus(21L, "PENDING")).thenReturn(List.of(pending));
        new ValveCommandIssuer(repo, events, new ObjectMapper(), Clock.systemUTC(), false, "")
                .onEnvelope(envelope("delivery.failed.v1", 7L, "{\"deliveryId\":21,\"terminalState\":\"FAILED\"}"));
        assertEquals("REVOKED", pending.getStatus());
    }

    private static EventEnvelope envelope(String type, Long provider, String payload) {
        return new EventEnvelope(UUID.randomUUID(), type, "SafetyDecision", "34", provider, 1L, NOW, payload);
    }
    private static String signature(ValveCommandEntity c) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String canonical = "v1|%s|%d|%s|%s|%s".formatted(c.getCommandId(), c.getDeliveryId(), c.getAction(), c.getNonce(), c.getExpiresAt());
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
