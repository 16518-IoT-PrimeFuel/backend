package com.primefuel.fulltank.platform.tracking;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.EventPublicationPersistenceRepository;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
import com.primefuel.fulltank.platform.tracking.domain.repositories.DeliveryTrackingRepository;
import com.primefuel.fulltank.platform.tracking.domain.repositories.TransportEvidenceSampleRepository;
import com.primefuel.fulltank.platform.tracking.api.TransportEvidenceRecorder;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.TransportEvidenceController;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.TransportEvidenceAckResource;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.TransportEvidenceResource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T16-A transport evidence over HTTP (S16 redesign W6). It exercises the contract end to end: a valid
 * sample enters and advances the projection, a late sample is preserved without regressing it, a foreign
 * driver and a cross-tenant assignment are rejected (403), and load milestones accept or reject correctly.
 *
 * <p>The caller is never trusted for a {@code driverId}: the assigned driver and the tenant are resolved
 * from the delivery assignment and the fleet catalog, which is exactly what these tests probe by varying the
 * authenticated principal.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:transport_evidence;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class TransportEvidenceControllerTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private FleetRegistry fleetRegistry;

    @Autowired
    private DeliveryTrackingRepository trackingRepository;

    @Autowired
    private TransportEvidenceSampleRepository sampleRepository;

    @Autowired
    private EventPublicationPersistenceRepository publications;

    private long driver(long providerId, long userId) {
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(providerId, userId, "Tracking",
                "Driver", "L-TRK-" + SEQUENCE.incrementAndGet(), "999000777",
                "tracking-driver-" + SEQUENCE.incrementAndGet() + "@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long delivery(long providerId, long driverId) {
        var delivery = new Delivery(new CreateDeliveryCommand(8000L + SEQUENCE.incrementAndGet(), providerId,
                driverId, 77L, "2026-10-01", "seed"));
        delivery.dispatch();
        return deliveryRepository.save(delivery).getId();
    }

    private static RequestPostProcessor authFor(long userId, long providerId) {
        var principal = new UserDetailsImpl(userId, "user-" + userId, "encoded", null, providerId,
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void recordsAValidPositionAndAdvancesTheLatest() throws Exception {
        long providerId = 801L;
        long userId = 901L;
        long driverId = driver(providerId, userId);
        long deliveryId = delivery(providerId, driverId);

        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", deliveryId)
                        .with(authFor(userId, providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"POSITION","latitude":10.5,"longitude":-66.9,"accuracyMeters":8.0,
                                 "recordedAt":"2026-09-01T10:00:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("POSITION"))
                .andExpect(jsonPath("$.latestAdvanced").value(true))
                .andExpect(jsonPath("$.deliveryId").value(deliveryId));

        var tracking = trackingRepository.findByDeliveryId(deliveryId).orElseThrow();
        assertThat(tracking.getLastLatitude()).isEqualTo(10.5);
        assertThat(tracking.getLastLongitude()).isEqualTo(-66.9);
        assertThat(tracking.getLastPositionAt()).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
        assertThat(tracking.getDriverId()).isEqualTo(driverId);
        assertThat(sampleRepository.countByDeliveryId(deliveryId)).isEqualTo(1);
    }

    @Test
    void aRetriedEventIdReplaysTheOriginalAckWithoutStoringOrPublishingAgain() throws Exception {
        long providerId = 808L;
        long userId = 908L;
        long deliveryId = delivery(providerId, driver(providerId, userId));
        var auth = authFor(userId, providerId);
        var body = """
                {"type":"POSITION","latitude":10.5,"longitude":-66.9,
                 "recordedAt":"2026-09-01T10:00:00Z","eventId":"offline-retry-1"}""";

        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", deliveryId).with(auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        long evidenceId = sampleRepository.findByDeliveryId(deliveryId).getFirst().getId();

        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", deliveryId).with(auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value(evidenceId));

        assertThat(sampleRepository.countByDeliveryId(deliveryId)).isEqualTo(1);
        assertThat(publications.findByAggregateTypeAndAggregateIdOrderByIdAsc("DeliveryTracking",
                String.valueOf(deliveryId))).hasSize(1);
    }

    @Test
    void aConcurrentDuplicateReturnsTheCommittedAckAfterTheWriteRollsBack() {
        var recorder = mock(TransportEvidenceRecorder.class);
        var deliveries = mock(DeliveryTrackingLookup.class);
        var fleet = mock(FleetCatalog.class);
        var membership = mock(MembershipAccess.class);
        var controller = new TransportEvidenceController(recorder, deliveries, fleet, membership);
        var recordedAt = Instant.parse("2026-09-01T10:00:00Z");
        var ack = new TransportEvidenceRecorder.EvidenceAck(55L, 44L, "POSITION", null,
                true, recordedAt, true);
        when(membership.currentUserId()).thenReturn(Optional.of(33L));
        when(deliveries.findAssignedDelivery(44L)).thenReturn(Optional.of(
                new DeliveryTrackingLookup.AssignedDeliverySnapshot(44L, 1L, 22L, 11L, "DISPATCHED")));
        when(fleet.findDriver(11L)).thenReturn(Optional.of(new FleetCatalog.DriverSnapshot(
                11L, 22L, 33L, "A", "B", "L", "1", "a@example.test", "AVAILABLE", true)));
        when(recorder.recordPosition(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(recorder.findReplay(44L, "retry-1")).thenReturn(Optional.of(ack));

        var response = controller.record(44L, new TransportEvidenceResource("POSITION", 10.5, -66.9,
                null, null, null, null, recordedAt, "retry-1"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((TransportEvidenceAckResource) response.getBody()).evidenceId()).isEqualTo(55L);
    }

    @Test
    void rejectsADriverThatIsNotTheAssignedOne() throws Exception {
        long providerId = 802L;
        long assignedUserId = 902L;
        long otherUserId = 903L;
        long deliveryId = delivery(providerId, driver(providerId, assignedUserId));

        // Same tenant, but the caller is a different driver: forbidden, and no evidence is stored.
        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", deliveryId)
                        .with(authFor(otherUserId, providerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"POSITION","latitude":1.0,"longitude":2.0,
                                 "recordedAt":"2026-09-01T10:00:00Z"}"""))
                .andExpect(status().isForbidden());

        assertThat(sampleRepository.countByDeliveryId(deliveryId)).isZero();
        assertThat(trackingRepository.findByDeliveryId(deliveryId)).isEmpty();
    }

    @Test
    void rejectsAnAssignmentThatCrossesTenants() throws Exception {
        long deliveryProviderId = 803L;
        long otherProviderId = 804L;
        long driverUserId = 904L;
        // The delivery belongs to provider 803 but is assigned to a driver that belongs to provider 804.
        long deliveryId = delivery(deliveryProviderId, driver(otherProviderId, driverUserId));

        // Even the assigned driver's user cannot report evidence across the tenant boundary.
        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", deliveryId)
                        .with(authFor(driverUserId, otherProviderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"POSITION","latitude":1.0,"longitude":2.0,
                                 "recordedAt":"2026-09-01T10:00:00Z"}"""))
                .andExpect(status().isForbidden());

        assertThat(sampleRepository.countByDeliveryId(deliveryId)).isZero();
    }

    @Test
    void aLateSampleDoesNotRegressTheLatestButIsKeptAsRawEvidence() throws Exception {
        long providerId = 805L;
        long userId = 905L;
        long deliveryId = delivery(providerId, driver(providerId, userId));
        var auth = authFor(userId, providerId);

        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", deliveryId).with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"POSITION","latitude":20.0,"longitude":30.0,
                                 "recordedAt":"2026-09-01T10:10:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latestAdvanced").value(true));

        // A later-arriving but older observation: accepted, stored raw, but the latest stays put.
        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", deliveryId).with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"POSITION","latitude":21.0,"longitude":31.0,
                                 "recordedAt":"2026-09-01T10:05:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latestAdvanced").value(false));

        var tracking = trackingRepository.findByDeliveryId(deliveryId).orElseThrow();
        assertThat(tracking.getLastPositionAt()).isEqualTo(Instant.parse("2026-09-01T10:10:00Z"));
        assertThat(tracking.getLastLatitude()).isEqualTo(20.0);
        // Both samples are preserved as raw evidence.
        assertThat(sampleRepository.countByDeliveryId(deliveryId)).isEqualTo(2);
    }

    @Test
    void acceptsValidLoadMilestonesAndRejectsImpossibleOnes() throws Exception {
        long providerId = 806L;
        long userId = 906L;
        long deliveryId = delivery(providerId, driver(providerId, userId));
        var auth = authFor(userId, providerId);

        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", deliveryId).with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"LOAD","milestone":"LOADED","volume":120.0,"unit":"LITRE",
                                 "recordedAt":"2026-09-01T09:00:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("LOAD"))
                .andExpect(jsonPath("$.milestone").value("LOADED"));

        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", deliveryId).with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"LOAD","milestone":"UNLOADED","recordedAt":"2026-09-01T12:00:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.milestone").value("UNLOADED"));

        // Unknown milestone: malformed body (400).
        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", deliveryId).with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"LOAD","milestone":"BANANA"}"""))
                .andExpect(status().isBadRequest());

        // Non-positive volume: malformed body (400).
        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", deliveryId).with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"LOAD","milestone":"LOADED","volume":-1.0,"unit":"LITRE"}"""))
                .andExpect(status().isBadRequest());

        // UNLOADED before any LOADED on a fresh delivery: impossible sequence (422).
        long freshDeliveryId = delivery(807L, driver(807L, 907L));
        mockMvc.perform(post("/api/v2/deliveries/{id}/transport-evidence", freshDeliveryId)
                        .with(authFor(907L, 807L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"LOAD","milestone":"UNLOADED"}"""))
                .andExpect(status().isUnprocessableEntity());

        var tracking = trackingRepository.findByDeliveryId(deliveryId).orElseThrow();
        assertThat(tracking.getLastLoadMilestone()).isEqualTo(LoadMilestone.UNLOADED);
    }
}
