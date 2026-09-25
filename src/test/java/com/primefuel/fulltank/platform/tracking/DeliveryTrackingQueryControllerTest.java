package com.primefuel.fulltank.platform.tracking;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.tracking.api.TransportEvidenceRecorder;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordLoadEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordPositionEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T16-B transport-tracking query over HTTP (S16). It proves the read side: the latest and the trail are
 * visible to the owning provider and to the assigned driver, a foreign tenant is rejected, the trail is
 * ordered by the device clock even when samples arrive out of order (jitter), and the reserved retention
 * contract answers "not available" without exposing data.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:tracking_query;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class DeliveryTrackingQueryControllerTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private FleetRegistry fleetRegistry;

    @Autowired
    private TransportEvidenceRecorder recorder;

    private long driver(long providerId, long userId) {
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(providerId, userId, "Query",
                "Driver", "L-TRK-Q-" + SEQUENCE.incrementAndGet(), "999000888",
                "query-driver-" + SEQUENCE.incrementAndGet() + "@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long delivery(long providerId, long driverId) {
        var delivery = new Delivery(new CreateDeliveryCommand(9000L + SEQUENCE.incrementAndGet(), providerId,
                driverId, 77L, "2026-10-01", "seed"));
        delivery.dispatch();
        return deliveryRepository.save(delivery).getId();
    }

    private static RequestPostProcessor authFor(long userId, Long providerId) {
        var principal = new UserDetailsImpl(userId, "user-" + userId, "encoded", null, providerId,
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void position(long deliveryId, long providerId, long driverId, double latitude,
                          String recordedAt) {
        var result = recorder.recordPosition(new RecordPositionEvidenceCommand(deliveryId, providerId, driverId,
                latitude, -66.0, 5.0, Instant.parse(recordedAt), null));
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void theAssignedDriverAndTheOwningProviderBothSeeTheLatest() throws Exception {
        long providerId = 11L;
        long driverUserId = 111L;
        long driverId = driver(providerId, driverUserId);
        long deliveryId = delivery(providerId, driverId);
        position(deliveryId, providerId, driverId, 10.5, "2026-09-01T10:00:00Z");

        // Assigned driver.
        mockMvc.perform(get("/api/v2/deliveries/{id}/tracking", deliveryId)
                        .with(authFor(driverUserId, providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastLatitude").value(10.5))
                .andExpect(jsonPath("$.lastPositionAt").value("2026-09-01T10:00:00Z"));

        // The assigned driver is allowed through identity alone, even without a provider identity.
        mockMvc.perform(get("/api/v2/deliveries/{id}/tracking", deliveryId)
                        .with(authFor(driverUserId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastLatitude").value(10.5));

        // Owning provider (a different principal of the same tenant).
        mockMvc.perform(get("/api/v2/deliveries/{id}/tracking", deliveryId)
                        .with(authFor(999L, providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastLatitude").value(10.5));
    }

    @Test
    void theTrailIsOrderedByTheDeviceClockEvenWhenSamplesArriveOutOfOrder() throws Exception {
        long providerId = 12L;
        long driverUserId = 112L;
        long driverId = driver(providerId, driverUserId);
        long deliveryId = delivery(providerId, driverId);

        // Arrival order is 10:10, then 10:00, then 10:05 (jitter); latitude identifies each observation.
        position(deliveryId, providerId, driverId, 30.0, "2026-09-01T10:10:00Z");
        position(deliveryId, providerId, driverId, 10.0, "2026-09-01T10:00:00Z");
        position(deliveryId, providerId, driverId, 20.0, "2026-09-01T10:05:00Z");

        mockMvc.perform(get("/api/v2/deliveries/{id}/tracking/samples", deliveryId)
                        .with(authFor(driverUserId, providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // Ordered by recordedAt, not by arrival.
                .andExpect(jsonPath("$[0].latitude").value(10.0))
                .andExpect(jsonPath("$[0].recordedAt").value("2026-09-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].latestAdvanced").value(false))
                .andExpect(jsonPath("$[1].latitude").value(20.0))
                .andExpect(jsonPath("$[1].recordedAt").value("2026-09-01T10:05:00Z"))
                .andExpect(jsonPath("$[1].latestAdvanced").value(false))
                .andExpect(jsonPath("$[2].latitude").value(30.0))
                .andExpect(jsonPath("$[2].recordedAt").value("2026-09-01T10:10:00Z"))
                .andExpect(jsonPath("$[2].latestAdvanced").value(true));

        // The latest is the newest recordedAt, regardless of arrival order.
        mockMvc.perform(get("/api/v2/deliveries/{id}/tracking", deliveryId)
                        .with(authFor(driverUserId, providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastLatitude").value(30.0))
                .andExpect(jsonPath("$.lastPositionAt").value("2026-09-01T10:10:00Z"));
    }

    @Test
    void aForeignTenantCannotReadTheTracking() throws Exception {
        long providerId = 13L;
        long driverId = driver(providerId, 113L);
        long deliveryId = delivery(providerId, driverId);
        position(deliveryId, providerId, driverId, 1.0, "2026-09-01T10:00:00Z");

        mockMvc.perform(get("/api/v2/deliveries/{id}/tracking", deliveryId)
                        .with(authFor(777L, 999L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/deliveries/{id}/tracking/samples", deliveryId)
                        .with(authFor(777L, 999L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDeliveryWithoutTrackingIsNotFound() throws Exception {
        long providerId = 14L;
        long driverUserId = 114L;
        long deliveryId = delivery(providerId, driver(providerId, driverUserId));

        mockMvc.perform(get("/api/v2/deliveries/{id}/tracking", deliveryId)
                        .with(authFor(driverUserId, providerId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theReservedRetentionContractAnswersNotAvailableWithoutExposingData() throws Exception {
        long providerId = 15L;
        long driverId = driver(providerId, 115L);
        long deliveryId = delivery(providerId, driverId);
        position(deliveryId, providerId, driverId, 42.0, "2026-09-01T10:00:00Z");

        mockMvc.perform(delete("/api/v2/admin/deliveries/{id}/transport-evidence", deliveryId)
                        .with(authFor(115L, providerId)))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.lastLatitude").doesNotExist());

        mockMvc.perform(get("/api/v2/admin/deliveries/{id}/transport-evidence/export", deliveryId)
                        .with(authFor(115L, providerId)))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.lastLatitude").doesNotExist());
    }

    @Test
    void loadStateIsVisibleInTheLatest() throws Exception {
        long providerId = 16L;
        long driverUserId = 116L;
        long driverId = driver(providerId, driverUserId);
        long deliveryId = delivery(providerId, driverId);

        assertThat(recorder.recordLoad(new RecordLoadEvidenceCommand(deliveryId, providerId, driverId,
                LoadMilestone.LOADED, 120.0, "LITRE", Instant.parse("2026-09-01T09:00:00Z"), null)).isSuccess()).isTrue();

        mockMvc.perform(get("/api/v2/deliveries/{id}/tracking", deliveryId)
                        .with(authFor(driverUserId, providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(true))
                .andExpect(jsonPath("$.lastLoadMilestone").value("LOADED"))
                .andExpect(jsonPath("$.lastLoadVolume").value(120.0));
    }
}
