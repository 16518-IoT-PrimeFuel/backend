package com.primefuel.fulltank.platform.safety.valve;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.SafetyIncidentRepository;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.ValveCommandEntity;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.ValveCommandRepository;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.EventPublicationPersistenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:valve_observation;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class ValveObservationsControllerTest {
    private static final AtomicLong IDS = new AtomicLong();
    @Autowired MockMvc mvc;
    @Autowired FleetRegistry fleet;
    @Autowired DeliveryRepository deliveries;
    @Autowired ValveCommandRepository commands;
    @Autowired SafetyIncidentRepository incidents;
    @Autowired EventPublicationPersistenceRepository publications;

    private long delivery(long provider, long user) {
        long n = IDS.incrementAndGet();
        var driver = fleet.registerDriver(new RegisterDriverCommand(provider, user, "Valve", "Driver", "V-" + n,
                "999000777", "valve-" + n + "@example.test", "AVAILABLE")).getOrElse(null);
        var delivery = new Delivery(new CreateDeliveryCommand(95000L + n, provider, driver.id(), 77L, "2026-10-01", "seed"));
        delivery.dispatch(); return deliveries.save(delivery).getId();
    }

    private ValveCommandEntity pending(long delivery, long provider, Instant expiry) {
        var c = new ValveCommandEntity(); c.setCommandId("cmd-" + IDS.incrementAndGet()); c.setDeliveryId(delivery);
        c.setProviderId(provider); c.setDecisionId(100L + IDS.incrementAndGet()); c.setAction("OPEN");
        c.setNonce("nonce-" + IDS.incrementAndGet()); c.setIssuedAt(Instant.now().minusSeconds(10));
        c.setExpiresAt(expiry); c.setSignature("a".repeat(64)); c.setProtocolVersion("v1"); c.setStatus("PENDING");
        return commands.save(c);
    }

    private static RequestPostProcessor auth(long user, long provider) {
        var principal = new UserDetailsImpl(user, "driver-" + user, "encoded", null, provider,
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private org.springframework.test.web.servlet.ResultActions postObservation(long id, long user, long provider, String state, String command) throws Exception {
        String commandJson = command == null ? "" : ",\"commandId\":\"" + command + "\"";
        return mvc.perform(post("/api/v2/deliveries/{id}/valve-observations", id).with(auth(user, provider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"" + state
                        + "\",\"observedAt\":\"2026-09-26T12:00:00Z\"" + commandJson + "}"));
    }

    @Test void validAckMarksCommandAndPublishesObservation() throws Exception {
        long provider = 5101, user = 6101, id = delivery(provider, user);
        var command = pending(id, provider, Instant.now().plusSeconds(90));
        postObservation(id, user, provider, "OPEN", command.getCommandId()).andExpect(status().isOk());
        assertThat(commands.findByDeliveryIdAndCommandId(id, command.getCommandId()).orElseThrow().getStatus()).isEqualTo("ACKED");
        assertThat(publications.findByAggregateTypeAndAggregateIdOrderByIdAsc("Delivery", String.valueOf(id)))
                .anyMatch(e -> e.getEventType().equals("valve.state.observed.v1"));
    }

    @Test void replayedAckIsIdempotentAndDoesNotPublishAgain() throws Exception {
        long provider = 5102, user = 6102, id = delivery(provider, user);
        var command = pending(id, provider, Instant.now().plusSeconds(90));
        postObservation(id, user, provider, "OPEN", command.getCommandId()).andExpect(status().isOk());
        postObservation(id, user, provider, "OPEN", command.getCommandId()).andExpect(status().isOk());
        assertThat(publications.findByAggregateTypeAndAggregateIdOrderByIdAsc("Delivery", String.valueOf(id)).stream()
                .filter(e -> e.getEventType().equals("valve.state.observed.v1")).count()).isEqualTo(1);
    }

    @Test void expiredAckCreatesIncident() throws Exception {
        long provider = 5103, user = 6103, id = delivery(provider, user);
        var command = pending(id, provider, Instant.now().minusSeconds(3));
        postObservation(id, user, provider, "OPEN", command.getCommandId()).andExpect(status().isAccepted());
        assertThat(commands.findByDeliveryIdAndCommandId(id, command.getCommandId()).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThat(incidents.findByDeliveryIdOrderByOccurredAtAsc(id)).hasSize(1);
    }

    @Test void spontaneousOpenCreatesIncidentAndEvent() throws Exception {
        long provider = 5104, user = 6104, id = delivery(provider, user);
        postObservation(id, user, provider, "OPEN", null).andExpect(status().isAccepted());
        assertThat(incidents.findByDeliveryIdOrderByOccurredAtAsc(id)).hasSize(1);
        assertThat(publications.findByAggregateTypeAndAggregateIdOrderByIdAsc("Delivery", String.valueOf(id)))
                .anyMatch(e -> e.getEventType().equals("safety.incident.detected.v1"));
    }

    @Test void missingCommandIdCorrelatesOnePendingCommand() throws Exception {
        long provider = 5105, user = 6105, id = delivery(provider, user);
        var command = pending(id, provider, Instant.now().plusSeconds(90));
        postObservation(id, user, provider, "OPEN", null).andExpect(status().isOk());
        assertThat(commands.findByDeliveryIdAndCommandId(id, command.getCommandId()).orElseThrow().getStatus()).isEqualTo("ACKED");
    }

    @Test void anotherDriverIsForbidden() throws Exception {
        long provider = 5106, assignedUser = 6106, id = delivery(provider, assignedUser);
        postObservation(id, assignedUser + 1, provider, "CLOSED", null).andExpect(status().isForbidden());
    }

    @Test void commandFromAnotherDeliveryBecomesIncidentWithoutCrossAck() throws Exception {
        long provider = 5107, user = 6107, requestedDelivery = delivery(provider, user);
        long foreignDelivery = delivery(provider, user + 1);
        var foreign = pending(foreignDelivery, provider, Instant.now().plusSeconds(90));
        postObservation(requestedDelivery, user, provider, "OPEN", foreign.getCommandId()).andExpect(status().isAccepted());
        assertThat(commands.findByDeliveryIdAndCommandId(foreignDelivery, foreign.getCommandId()).orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(incidents.findByDeliveryIdOrderByOccurredAtAsc(requestedDelivery)).hasSize(1);
    }
}
