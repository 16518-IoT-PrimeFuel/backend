package com.primefuel.fulltank.platform.safety;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.safety.api.GeofenceEvaluation;
import com.primefuel.fulltank.platform.safety.api.GeofencePolicies;
import com.primefuel.fulltank.platform.safety.domain.model.commands.CreateGeofencePolicyCommand;
import com.primefuel.fulltank.platform.safety.domain.repositories.SafetyDecisionRepository;
import com.primefuel.fulltank.platform.tracking.api.TransportEvidenceRecorder;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordPositionEvidenceCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T17-A geofence decision end-to-end (S17): the admin endpoint versions a policy, the decision seam
 * evaluates it against the latest tracking position and records the outcome append-only, and the injected
 * clock makes freshness deterministic. It also fixes the append-only invariant — a new policy version never
 * rewrites an already-persisted decision.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:geofence_evaluation;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class GeofenceEvaluationTest {

    private static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");
    private static final MutableClock CLOCK = new MutableClock(T0);
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @TestConfiguration
    static class ClockTestConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GeofenceEvaluation geofenceEvaluation;

    @Autowired
    private GeofencePolicies geofencePolicies;

    @Autowired
    private SafetyDecisionRepository safetyDecisionRepository;

    @Autowired
    private TransportEvidenceRecorder recorder;

    @Autowired
    private FleetRegistry fleetRegistry;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @BeforeEach
    void resetClock() {
        CLOCK.set(T0);
    }

    private long driver(long providerId, long userId) {
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(providerId, userId, "Geofence",
                "Driver", "L-GEO-" + SEQUENCE.incrementAndGet(), "999000111",
                "geofence-driver-" + SEQUENCE.incrementAndGet() + "@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long delivery(long providerId, long driverId) {
        var delivery = new Delivery(new CreateDeliveryCommand(9600L + SEQUENCE.incrementAndGet(), providerId,
                driverId, 77L, "2026-10-01", "seed"));
        delivery.dispatch();
        return deliveryRepository.save(delivery).getId();
    }

    private void position(long deliveryId, long providerId, long driverId, double latitude, double longitude,
                          Double accuracy, Instant recordedAt) {
        assertThat(recorder.recordPosition(new RecordPositionEvidenceCommand(deliveryId, providerId, driverId,
                latitude, longitude, accuracy, recordedAt, null)).isSuccess()).isTrue();
    }

    private static RequestPostProcessor authForProvider(long providerId) {
        var principal = new UserDetailsImpl(providerId, "provider-" + providerId, "encoded", null, providerId,
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void anAuthorizedDecisionIsRecordedForAFreshPositionInsideTheRadius() throws Exception {
        long providerId = 41L;
        long driverId = driver(providerId, 141L);
        long deliveryId = delivery(providerId, driverId);
        position(deliveryId, providerId, driverId, 10.0, 20.0, 10.0, T0);

        mockMvc.perform(post("/api/v2/deliveries/{id}/geofence-policies", deliveryId)
                        .with(authForProvider(providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerLatitude\":10.0,\"centerLongitude\":20.0,\"radiusMeters\":1000.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyVersion").value(1));

        var decision = geofenceEvaluation.evaluate(deliveryId).toOptional().orElseThrow();

        assertThat(decision.authorized()).isTrue();
        assertThat(decision.policyVersion()).isEqualTo(1);
        assertThat(decision.trackingEvidenceId()).isNotNull();
        assertThat(decision.evaluatedAt()).isEqualTo(T0);

        var persisted = safetyDecisionRepository.findByDeliveryId(deliveryId);
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).isAuthorized()).isTrue();
        assertThat(persisted.get(0).getPolicyVersion()).isEqualTo(1);
    }

    @Test
    void aForeignProviderCannotCreateAPolicy() throws Exception {
        long providerId = 42L;
        long deliveryId = delivery(providerId, driver(providerId, 142L));

        mockMvc.perform(post("/api/v2/deliveries/{id}/geofence-policies", deliveryId)
                        .with(authForProvider(999L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerLatitude\":10.0,\"centerLongitude\":20.0,\"radiusMeters\":1000.0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aNewPolicyVersionDoesNotAffectAlreadyPersistedDecisions() {
        long providerId = 43L;
        long driverId = driver(providerId, 143L);
        long deliveryId = delivery(providerId, driverId);
        position(deliveryId, providerId, driverId, 10.0, 20.0, 10.0, T0);

        var first = geofencePolicies.createPolicy(new CreateGeofencePolicyCommand(
                deliveryId, providerId, 10.0, 20.0, 1000.0)).toOptional().orElseThrow();
        assertThat(first.policyVersion()).isEqualTo(1);
        var decisionUnderV1 = geofenceEvaluation.evaluate(deliveryId).toOptional().orElseThrow();
        assertThat(decisionUnderV1.policyVersion()).isEqualTo(1);

        // Replace the policy with a new, tighter version.
        var second = geofencePolicies.createPolicy(new CreateGeofencePolicyCommand(
                deliveryId, providerId, 10.0, 20.0, 100.0)).toOptional().orElseThrow();
        assertThat(second.policyVersion()).isEqualTo(2);

        // The already-persisted decision still references v1 — history is not rewritten.
        var persisted = safetyDecisionRepository.findByDeliveryId(deliveryId);
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getPolicyVersion()).isEqualTo(1);

        // A new evaluation uses the version in force (v2) and appends a second decision.
        var decisionUnderV2 = geofenceEvaluation.evaluate(deliveryId).toOptional().orElseThrow();
        assertThat(decisionUnderV2.policyVersion()).isEqualTo(2);
        assertThat(safetyDecisionRepository.findByDeliveryId(deliveryId)).hasSize(2);
    }

    @Test
    void freshnessUsesTheInjectedClock() {
        long providerId = 44L;
        long driverId = driver(providerId, 144L);
        long deliveryId = delivery(providerId, driverId);
        position(deliveryId, providerId, driverId, 10.0, 20.0, 10.0, T0);
        geofencePolicies.createPolicy(new CreateGeofencePolicyCommand(deliveryId, providerId, 10.0, 20.0, 1000.0));

        // At T0 the position is fresh.
        assertThat(geofenceEvaluation.evaluate(deliveryId).toOptional().orElseThrow().authorized()).isTrue();

        // Six minutes later the same position is stale — the injected clock drives the decision.
        CLOCK.set(T0.plusSeconds(361));
        var stale = geofenceEvaluation.evaluate(deliveryId).toOptional().orElseThrow();

        assertThat(stale.authorized()).isFalse();
        assertThat(stale.reason()).isEqualTo("STALE");
    }

    @Test
    void aDeliveryWithoutAPolicyIsBlockedWithNoPolicyAndRecorded() {
        long providerId = 45L;
        long deliveryId = delivery(providerId, driver(providerId, 145L));

        var decision = geofenceEvaluation.evaluate(deliveryId).toOptional().orElseThrow();

        assertThat(decision.authorized()).isFalse();
        assertThat(decision.reason()).isEqualTo("NO_POLICY");
        assertThat(decision.policyId()).isNull();
        assertThat(decision.policyVersion()).isNull();
        var persisted = safetyDecisionRepository.findByDeliveryId(deliveryId);
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getProviderId()).isEqualTo(providerId);
        assertThat(persisted.get(0).getReason()).isEqualTo("NO_POLICY");
    }

    @Test
    void evaluatingAnUnknownDeliveryFails() {
        assertThat(geofenceEvaluation.evaluate(987654321L).isFailure()).isTrue();
    }

    @Test
    void aPolicyWithoutAnyTrackedPositionIsBlockedWithNoPosition() {
        long providerId = 46L;
        long deliveryId = delivery(providerId, driver(providerId, 146L));
        geofencePolicies.createPolicy(new CreateGeofencePolicyCommand(deliveryId, providerId, 10.0, 20.0, 1000.0));

        var decision = geofenceEvaluation.evaluate(deliveryId).toOptional().orElseThrow();

        assertThat(decision.authorized()).isFalse();
        assertThat(decision.reason()).isEqualTo("NO_POSITION");
        assertThat(decision.policyVersion()).isEqualTo(1);
    }

    @Test
    void aFutureDatedSampleIsRejectedByTheRecorderBeyondTheClockSkew() {
        long providerId = 47L;
        long driverId = driver(providerId, 147L);
        long deliveryId = delivery(providerId, driverId);

        var beyondSkew = recorder.recordPosition(new RecordPositionEvidenceCommand(deliveryId, providerId,
                driverId, 10.0, 20.0, 10.0, T0.plusSeconds(121), null));
        assertThat(beyondSkew.isFailure()).isTrue();

        // Within the tolerated skew it is accepted.
        position(deliveryId, providerId, driverId, 10.0, 20.0, 10.0, T0.plusSeconds(120));
    }

    @Test
    void aPolicyWithInvalidGeometryIsRejected() throws Exception {
        long providerId = 48L;
        long deliveryId = delivery(providerId, driver(providerId, 148L));

        mockMvc.perform(post("/api/v2/deliveries/{id}/geofence-policies", deliveryId)
                        .with(authForProvider(providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerLatitude\":91.0,\"centerLongitude\":20.0,\"radiusMeters\":1000.0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v2/deliveries/{id}/geofence-policies", deliveryId)
                        .with(authForProvider(providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerLatitude\":10.0,\"centerLongitude\":181.0,\"radiusMeters\":1000.0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v2/deliveries/{id}/geofence-policies", deliveryId)
                        .with(authForProvider(providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerLatitude\":10.0,\"centerLongitude\":20.0,\"radiusMeters\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPolicyForAMissingDeliveryIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v2/deliveries/{id}/geofence-policies", 987654321L)
                        .with(authForProvider(49L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerLatitude\":10.0,\"centerLongitude\":20.0,\"radiusMeters\":1000.0}"))
                .andExpect(status().isNotFound());
    }

    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
