package com.primefuel.fulltank.platform.fulfillment;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices.DeliveryLifecycleServiceImpl;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.*;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryStateTransitionRepository;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DeliveryBusinessJournalRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.safety.api.GeofencePolicies;
import com.primefuel.fulltank.platform.safety.domain.model.commands.CreateGeofencePolicyCommand;
import com.primefuel.fulltank.platform.safety.domain.repositories.SafetyDecisionRepository;
import com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence.ValveCommandRepository;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.EventPublicationPersistenceRepository;
import com.primefuel.fulltank.platform.tracking.api.TransportEvidenceRecorder;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordLoadEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordPositionEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
import com.primefuel.fulltank.platform.tracking.domain.repositories.TransportEvidenceSampleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:delivery_timeline;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "authorization.jwt.secret=0123456789abcdef0123456789abcdef",
        "safety.valve.commands.enabled=true", "safety.valve.signing-secret=timeline-test-secret"
})
@AutoConfigureMockMvc
class DeliveryTimelineControllerTest {
    private static final AtomicLong IDS = new AtomicLong(12000);
    @Autowired MockMvc mvc;
    @Autowired FleetRegistry fleet;
    @Autowired DeliveryRepository deliveries;
    @Autowired DeliveryLifecycleServiceImpl lifecycle;
    @Autowired DeliveryStateTransitionRepository transitions;
    @Autowired DeliveryBusinessJournalRepository journal;
    @Autowired GeofencePolicies policies;
    @Autowired TransportEvidenceRecorder evidence;
    @Autowired SafetyDecisionRepository decisions;
    @Autowired ValveCommandRepository commands;
    @Autowired TransportEvidenceSampleRepository samples;
    @Autowired EventPublicationPersistenceRepository publications;
    @MockitoBean FuelOrderQueryService fuelOrders;

    private record Fixture(long id, long provider, long user, long driver) { }
    private Fixture fixture() {
        long n = IDS.incrementAndGet(), provider = 7400 + n, user = 8400 + n;
        var driver = fleet.registerDriver(new RegisterDriverCommand(provider, user, "Timeline", "Driver", "T-" + n,
                "999000777", "timeline-" + n + "@example.test", "AVAILABLE")).getOrElse(null);
        var delivery = new Delivery(new CreateDeliveryCommand(100000L + n, provider, driver.id(), 22L, "2026-10-01", "seed"));
        delivery.dispatch(); delivery = deliveries.save(delivery);
        when(fuelOrders.findRequestedQuantity(anyLong())).thenReturn(Optional.of(100.0));
        return new Fixture(delivery.getId(), provider, user, driver.id());
    }
    private static RequestPostProcessor auth(long user, long provider, String role) {
        var principal = new UserDetailsImpl(user, "timeline-" + user, "encoded", null, provider,
                List.of(new SimpleGrantedAuthority(role)));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test void completeLifecycleProducesOrderedTimelineWithSafetyDecisionAndValveCommand() throws Exception {
        var f = fixture();
        lifecycle.handle(new AssignDeliveryCommand(f.id())); lifecycle.handle(new StartDeliveryCommand(f.id()));
        lifecycle.handle(new ArriveDeliveryCommand(f.id()));
        policies.createPolicy(new CreateGeofencePolicyCommand(f.id(), f.provider(), 10.0, 20.0, 1000.0));
        evidence.recordPosition(new RecordPositionEvidenceCommand(f.id(), f.provider(), f.driver(), 10.0, 20.0,
                10.0, Instant.now(), null));
        assertThat(lifecycle.handle(new CompletePhysicalDeliveryCommand(f.id(), 70.0)).isSuccess()).isTrue();

        mvc.perform(get("/api/v2/deliveries/{id}/timeline", f.id()).with(auth(f.user(), f.provider(), "ROLE_PROVIDER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.type=='STATE_TRANSITION')]").isArray())
                .andExpect(jsonPath("$[?(@.type=='SAFETY_DECISION' && @.summary=='AUTHORIZED')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.type=='VALVE_COMMAND' && @.summary=='OPEN')]").isNotEmpty());
        assertThat(decisions.findByDeliveryId(f.id())).hasSize(1);
        assertThat(commands.findByDeliveryIdAndStatus(f.id(), "PENDING")).hasSize(1);
        var items = journal.findByDeliveryIdOrderByOccurredAtAsc(f.id());
        assertThat(items).extracting(e -> e.getType()).contains("SAFETY_DECISION", "VALVE_COMMAND");
    }

    @Test void preV17DeliveryShowsLegacyGap() throws Exception {
        var f = fixture();
        mvc.perform(get("/api/v2/deliveries/{id}/timeline", f.id()).with(auth(f.user(), f.provider(), "ROLE_PROVIDER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("LEGACY_GAP"));
    }

    @Test void foreignTenantCannotReadTimeline() throws Exception {
        var f = fixture();
        mvc.perform(get("/api/v2/deliveries/{id}/timeline", f.id()).with(auth(f.user() + 90000, f.provider() + 90000, "ROLE_PROVIDER")))
                .andExpect(status().isForbidden());
    }

    @Test void deletingGpsRemovesLoadMilestonesButKeepsBusinessJournal() throws Exception {
        var f = fixture();
        lifecycle.handle(new AssignDeliveryCommand(f.id())); lifecycle.handle(new StartDeliveryCommand(f.id()));
        lifecycle.handle(new ArriveDeliveryCommand(f.id()));
        evidence.recordLoad(new RecordLoadEvidenceCommand(f.id(), f.provider(), f.driver(), LoadMilestone.LOADED,
                50.0, "L", Instant.now(), null));
        policies.createPolicy(new CreateGeofencePolicyCommand(f.id(), f.provider(), 10.0, 20.0, 1000.0));
        evidence.recordPosition(new RecordPositionEvidenceCommand(f.id(), f.provider(), f.driver(), 10.0, 20.0,
                10.0, Instant.now(), null));
        assertThat(lifecycle.handle(new CompletePhysicalDeliveryCommand(f.id(), 50.0)).isSuccess()).isTrue();
        mvc.perform(get("/api/v2/deliveries/{id}/timeline", f.id()).with(auth(f.user(), f.provider(), "ROLE_PROVIDER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.type=='LOAD_MILESTONE')]").isNotEmpty());
        mvc.perform(delete("/api/v2/admin/deliveries/{id}/transport-evidence", f.id())
                        .with(auth(999999, 1, "ROLE_ADMIN"))).andExpect(status().isNoContent());
        assertThat(samples.countByDeliveryId(f.id())).isZero();
        mvc.perform(get("/api/v2/deliveries/{id}/timeline", f.id()).with(auth(f.user(), f.provider(), "ROLE_PROVIDER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.type=='LOAD_MILESTONE')]").isEmpty())
                .andExpect(jsonPath("$[?(@.type=='STATE_TRANSITION')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.type=='SAFETY_DECISION')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.type=='VALVE_COMMAND')]").isNotEmpty());
    }
}
