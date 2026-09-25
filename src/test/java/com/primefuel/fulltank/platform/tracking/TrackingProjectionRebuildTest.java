package com.primefuel.fulltank.platform.tracking;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.tracking.api.DeliveryTrackingQuery;
import com.primefuel.fulltank.platform.tracking.api.TrackingProjectionRebuilder;
import com.primefuel.fulltank.platform.tracking.api.TransportEvidenceRecorder;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordLoadEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordPositionEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T16-B rebuild determinism (S16). The projection is a derived value: replaying the raw evidence, in device
 * clock order, must reproduce exactly the live projection — even when the samples arrived out of order
 * (jitter). This is the guarantee that lets the projection be recomputed if it is ever corrupted or drifted.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:tracking_rebuild;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class TrackingProjectionRebuildTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired
    private TransportEvidenceRecorder recorder;

    @Autowired
    private DeliveryTrackingQuery query;

    @Autowired
    private TrackingProjectionRebuilder rebuilder;

    @Autowired
    private FleetRegistry fleetRegistry;

    @Autowired
    private DeliveryRepository deliveryRepository;

    private long driver(long providerId, long userId) {
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(providerId, userId, "Rebuild",
                "Driver", "L-TRK-R-" + SEQUENCE.incrementAndGet(), "999000999",
                "rebuild-driver-" + SEQUENCE.incrementAndGet() + "@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long delivery(long providerId, long driverId) {
        var delivery = new Delivery(new CreateDeliveryCommand(9500L + SEQUENCE.incrementAndGet(), providerId,
                driverId, 77L, "2026-10-01", "seed"));
        delivery.dispatch();
        return deliveryRepository.save(delivery).getId();
    }

    private void position(long deliveryId, long providerId, long driverId, double latitude, String recordedAt) {
        assertThat(recorder.recordPosition(new RecordPositionEvidenceCommand(deliveryId, providerId, driverId,
                latitude, -66.0, 5.0, Instant.parse(recordedAt), null)).isSuccess()).isTrue();
    }

    private void load(long deliveryId, long providerId, long driverId, LoadMilestone milestone, Double volume,
                      String recordedAt) {
        assertThat(recorder.recordLoad(new RecordLoadEvidenceCommand(deliveryId, providerId, driverId, milestone,
                volume, "LITRE", Instant.parse(recordedAt), null)).isSuccess()).isTrue();
    }

    @Test
    void rebuildingReproducesTheLiveProjectionEvenWithJitter() {
        long providerId = 21L;
        long driverId = driver(providerId, 121L);
        long deliveryId = delivery(providerId, driverId);

        // Arrival is jittered: LOADED first, then positions out of recordedAt order, then UNLOADED.
        load(deliveryId, providerId, driverId, LoadMilestone.LOADED, 100.0, "2026-09-01T09:00:00Z");
        position(deliveryId, providerId, driverId, 30.0, "2026-09-01T10:10:00Z");
        position(deliveryId, providerId, driverId, 10.0, "2026-09-01T10:00:00Z");
        position(deliveryId, providerId, driverId, 20.0, "2026-09-01T10:05:00Z");
        load(deliveryId, providerId, driverId, LoadMilestone.UNLOADED, null, "2026-09-01T11:00:00Z");

        var live = query.latest(deliveryId).orElseThrow();

        var rebuilt = rebuilder.rebuild(deliveryId).orElseThrow();

        // Field-for-field identical to the live projection (version is the optimistic-lock counter).
        assertThat(rebuilt).usingRecursiveComparison().ignoringFields("version").isEqualTo(live);
        // And the persisted projection is now the rebuilt one.
        assertThat(query.latest(deliveryId).orElseThrow())
                .usingRecursiveComparison().ignoringFields("version").isEqualTo(rebuilt);

        // Deterministic: a second rebuild yields the same result.
        assertThat(rebuilder.rebuild(deliveryId).orElseThrow())
                .usingRecursiveComparison().ignoringFields("version").isEqualTo(rebuilt);
    }

    @Test
    void rebuildingReproducesTheProjectionForOutOfOrderLoadMilestones() {
        long providerId = 23L;
        long driverId = driver(providerId, 123L);

        // Jitter: an UNLOADED arrives before the LOADED that precedes it chronologically; the LOADED is late
        // (recordedAt 09:00 < the already-applied UNLOADED at 11:00) and must not regress the latest.
        long jitteredDeliveryId = delivery(providerId, driverId);
        load(jitteredDeliveryId, providerId, driverId, LoadMilestone.LOADED, 80.0, "2026-09-01T08:00:00Z");
        load(jitteredDeliveryId, providerId, driverId, LoadMilestone.UNLOADED, null, "2026-09-01T11:00:00Z");
        load(jitteredDeliveryId, providerId, driverId, LoadMilestone.LOADED, 80.0, "2026-09-01T09:00:00Z");

        var live = query.latest(jitteredDeliveryId).orElseThrow();
        // The late LOADED did not move the latest load state.
        assertThat(live.loaded()).isFalse();
        assertThat(live.lastLoadMilestone()).isEqualTo("UNLOADED");
        assertThat(live.lastLoadAt()).isEqualTo(Instant.parse("2026-09-01T11:00:00Z"));

        var rebuilt = rebuilder.rebuild(jitteredDeliveryId).orElseThrow();
        assertThat(rebuilt).usingRecursiveComparison().ignoringFields("version").isEqualTo(live);

        // Same milestones received in correct (chronological) order produce the same load state.
        long orderedDeliveryId = delivery(providerId, driverId);
        load(orderedDeliveryId, providerId, driverId, LoadMilestone.LOADED, 80.0, "2026-09-01T08:00:00Z");
        load(orderedDeliveryId, providerId, driverId, LoadMilestone.LOADED, 80.0, "2026-09-01T09:00:00Z");
        load(orderedDeliveryId, providerId, driverId, LoadMilestone.UNLOADED, null, "2026-09-01T11:00:00Z");
        var orderedLive = query.latest(orderedDeliveryId).orElseThrow();

        assertThat(rebuilt.loaded()).isEqualTo(orderedLive.loaded());
        assertThat(rebuilt.lastLoadMilestone()).isEqualTo(orderedLive.lastLoadMilestone());
        assertThat(rebuilt.lastLoadAt()).isEqualTo(orderedLive.lastLoadAt());
        assertThat(rebuilt.lastLoadVolume()).isEqualTo(orderedLive.lastLoadVolume());
    }

    @Test
    void rebuildingSurvivesRawEvidenceWithAChronologicallyImpossibleLoadSequence() {
        long providerId = 24L;
        long driverId = driver(providerId, 124L);
        long deliveryId = delivery(providerId, driverId);

        // LOADED arrives first and advances (loaded=true, lastLoadAt=08:00). An UNLOADED then arrives whose
        // recordedAt (07:00) is BEFORE the already-applied LOADED: it is late, so it never reaches the
        // LOADED/UNLOADED sequence check live — it is simply not applied, and is still kept as raw evidence.
        load(deliveryId, providerId, driverId, LoadMilestone.LOADED, 50.0, "2026-09-01T08:00:00Z");
        load(deliveryId, providerId, driverId, LoadMilestone.UNLOADED, null, "2026-09-01T07:00:00Z");

        var live = query.latest(deliveryId).orElseThrow();
        assertThat(live.loaded()).isTrue();
        assertThat(live.lastLoadMilestone()).isEqualTo("LOADED");

        // A rebuild replays strictly by recordedAt: the UNLOADED@07:00 is processed FIRST, against a
        // freshly-reset (loaded=false) state — a chronologically impossible sequence the live path never
        // actually validated. The rebuild must not throw; it must treat that sample as not-advanced and
        // continue, converging on the same live state once the LOADED@08:00 is replayed.
        var rebuilt = rebuilder.rebuild(deliveryId).orElseThrow();
        assertThat(rebuilt).usingRecursiveComparison().ignoringFields("version").isEqualTo(live);
    }

    @Test
    void rebuildingADeliveryWithoutSamplesIsEmpty() {
        long providerId = 22L;
        long deliveryId = delivery(providerId, driver(providerId, 122L));

        assertThat(rebuilder.rebuild(deliveryId)).isEmpty();
    }
}
