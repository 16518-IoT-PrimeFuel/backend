package com.primefuel.fulltank.platform.fleet;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.api.FleetReservations;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.ReserveFleetCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.FleetReservationStatus;
import com.primefuel.fulltank.platform.fleet.domain.repositories.FleetReservationRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reservation model and its calculation — window/volume, usable-capacity check (with unit conversion,
 * U05) and overlap revalidation, all through the {@code fleet.api} seam (T13-A). T13-B adds the
 * {@code reference} idempotency and the release/expiry lifecycle here; the true concurrent barrier is
 * proved against real MySQL in {@link FleetReservationConcurrencyMySqlTest}.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:fleet_reservations;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class FleetReservationTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-10-01T11:00:00Z");

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private FleetRegistry fleetRegistry;

    @Autowired
    private FleetReservations fleetReservations;

    @Autowired
    private FleetReservationRepository reservationRepository;

    private long driver(long providerId) {
        int sequence = SEQUENCE.incrementAndGet();
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(
                providerId, null, "Res", "Driver", "L-RES-" + sequence, "999000111",
                "res-driver-" + sequence + "@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long tanker(long providerId, double capacity, String unit) {
        int sequence = SEQUENCE.incrementAndGet();
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(
                providerId, "RES-" + sequence, "Volvo", "FH", capacity, unit, "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private static ReserveFleetCommand reserve(long providerId, long driverId, long tankerId,
                                               Instant start, Instant end, double volume, String unit) {
        return new ReserveFleetCommand(providerId, driverId, tankerId, "res-" + SEQUENCE.incrementAndGet(),
                start, end, volume, unit);
    }

    @Test
    void reservesAResourceForAWindowAndVolume() {
        long providerId = 1L;
        var result = fleetReservations.reserve(reserve(providerId, driver(providerId), tanker(providerId, 1000.0, "LITRE"),
                T0, T2, 100.0, "LITRE"));

        assertThat(result.isSuccess()).isTrue();
        var snapshot = result.getOrElse(null);
        assertThat(snapshot.id()).isNotNull();
        assertThat(snapshot.providerId()).isEqualTo(providerId);
        assertThat(snapshot.status()).isEqualTo("ACTIVE");
        assertThat(snapshot.volume()).isEqualTo(100.0);
        assertThat(snapshot.unit()).isEqualTo("LITRE");
        assertThat(snapshot.windowStart()).isEqualTo(T0);
        assertThat(snapshot.windowEnd()).isEqualTo(T2);
    }

    @Test
    void acceptsAVolumeEqualToTheCapacityAndRejectsOneAboveIt() {
        long providerId = 1L;
        long capacityDriver = driver(providerId);
        long tankerId = tanker(providerId, 100.0, "LITRE");

        // usable capacity >= volume: equality passes.
        assertThat(fleetReservations.reserve(reserve(providerId, capacityDriver, tankerId, T0, T1, 100.0, "LITRE"))
                .isSuccess()).isTrue();
        // above the capacity: rejected (window touches the previous one, so this is not an overlap).
        assertThat(fleetReservations.reserve(reserve(providerId, capacityDriver, tankerId, T1, T2, 101.0, "LITRE"))
                .isFailure()).isTrue();
    }

    @Test
    void comparesCapacityAcrossUnits() {
        long providerId = 1L;
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 100.0, "LITRE");

        // 10 gallons ≈ 37.85 litres, under the 100-litre capacity.
        assertThat(fleetReservations.reserve(reserve(providerId, driverId, tankerId, T0, T1, 10.0, "GALLON"))
                .isSuccess()).isTrue();
        // 30 gallons ≈ 113.5 litres, above the 100-litre capacity (touching window, not an overlap).
        assertThat(fleetReservations.reserve(reserve(providerId, driverId, tankerId, T1, T2, 30.0, "GALLON"))
                .isFailure()).isTrue();
    }

    @Test
    void rejectsAnOverlappingWindowOnTheSameDriverOrTanker() {
        long providerId = 1L;
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0, "LITRE");
        assertThat(fleetReservations.reserve(reserve(providerId, driverId, tankerId, T0, T2, 100.0, "LITRE"))
                .isSuccess()).isTrue();

        // same driver + tanker, overlapping window.
        assertThat(fleetReservations.reserve(reserve(providerId, driverId, tankerId, T1, T3, 100.0, "LITRE"))
                .isFailure()).isTrue();
        // only the tanker is shared, still an overlap.
        assertThat(fleetReservations.reserve(reserve(providerId, driver(providerId), tankerId, T1, T3, 100.0, "LITRE"))
                .isFailure()).isTrue();
        // only the driver is shared, still an overlap.
        assertThat(fleetReservations.reserve(reserve(providerId, driverId, tanker(providerId, 1000.0, "LITRE"), T1, T3, 100.0, "LITRE"))
                .isFailure()).isTrue();
    }

    @Test
    void allowsBackToBackWindowsAndDisjointResources() {
        long providerId = 1L;
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0, "LITRE");
        assertThat(fleetReservations.reserve(reserve(providerId, driverId, tankerId, T0, T1, 100.0, "LITRE"))
                .isSuccess()).isTrue();
        // touching window on the same resource is allowed (half-open).
        assertThat(fleetReservations.reserve(reserve(providerId, driverId, tankerId, T1, T2, 100.0, "LITRE"))
                .isSuccess()).isTrue();
        // same window on different resources is allowed.
        assertThat(fleetReservations.reserve(reserve(providerId, driver(providerId),
                tanker(providerId, 1000.0, "LITRE"), T0, T1, 100.0, "LITRE")).isSuccess()).isTrue();
    }

    @Test
    void rejectsAResourceFromAnotherTenant() {
        long providerId = 1L;
        long foreignDriverId = driver(2L);
        long foreignTankerId = tanker(2L, 1000.0, "LITRE");

        Result<?, ?> result = fleetReservations.reserve(
                reserve(providerId, foreignDriverId, tanker(providerId, 1000.0, "LITRE"), T0, T1, 100.0, "LITRE"));

        assertThat(result.isFailure()).isTrue();

        var otherResult = fleetReservations.reserve(
                reserve(providerId, driver(providerId), foreignTankerId, T0, T1, 100.0, "LITRE"));
        assertThat(otherResult.isFailure()).isTrue();
    }

    @Test
    void rejectsAnInvalidWindowOrVolume() {
        long providerId = 1L;
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0, "LITRE");

        assertThat(fleetReservations.reserve(reserve(providerId, driverId, tankerId, T1, T1, 100.0, "LITRE"))
                .isFailure()).isTrue();
        assertThat(fleetReservations.reserve(reserve(providerId, driverId, tankerId, T0, T1, 0.0, "LITRE"))
                .isFailure()).isTrue();
    }

    @Test
    void replaysTheSameReferenceWithTheSameParametersAsOneReservation() {
        long providerId = 1L;
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0, "LITRE");

        var first = fleetReservations.reserve(
                reserveRef(providerId, driverId, tankerId, "order-replay", T0, T2, 100.0, "LITRE"));
        var replay = fleetReservations.reserve(
                reserveRef(providerId, driverId, tankerId, "order-replay", T0, T2, 100.0, "LITRE"));

        assertThat(first.isSuccess()).isTrue();
        assertThat(replay.isSuccess()).isTrue();
        assertThat(replay.getOrElse(null).id()).isEqualTo(first.getOrElse(null).id());
        assertThat(replay.getOrElse(null).status()).isEqualTo("ACTIVE");
        // The replay created no second hold on the same resource.
        assertThat(reservationRepository.findActiveByProvider(providerId).stream()
                .filter(reservation -> driverId == reservation.getDriverId()).count()).isEqualTo(1);
    }

    @Test
    void rejectsAReferenceReplayWhoseParametersDiffer() {
        long providerId = 1L;
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0, "LITRE");
        assertThat(fleetReservations.reserve(
                reserveRef(providerId, driverId, tankerId, "order-mismatch", T0, T2, 100.0, "LITRE"))
                .isSuccess()).isTrue();

        // The same reference with a different window is a real conflict, not a replay.
        Result<?, ApplicationError> conflict = fleetReservations.reserve(
                reserveRef(providerId, driverId, tankerId, "order-mismatch", T1, T3, 100.0, "LITRE"));

        assertThat(conflict.isFailure()).isTrue();
        assertThat(((Result.Failure<?, ApplicationError>) conflict).error().code())
                .isEqualTo("FLEETRESERVATION_CONFLICT");
    }

    @Test
    void releasesIdempotentlyAndFreesTheWindow() {
        long providerId = 1L;
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0, "LITRE");
        assertThat(fleetReservations.reserve(
                reserveRef(providerId, driverId, tankerId, "order-release", T0, T2, 100.0, "LITRE"))
                .isSuccess()).isTrue();

        var released = fleetReservations.release("order-release");
        assertThat(released.isSuccess()).isTrue();
        assertThat(released.getOrElse(null).status()).isEqualTo("RELEASED");

        var releasedAgain = fleetReservations.release("order-release");
        assertThat(releasedAgain.isSuccess()).isTrue();
        assertThat(releasedAgain.getOrElse(null).id()).isEqualTo(released.getOrElse(null).id());
        assertThat(releasedAgain.getOrElse(null).status()).isEqualTo("RELEASED");

        // Once released, the window is free again for a different reference.
        assertThat(fleetReservations.reserve(
                reserveRef(providerId, driverId, tankerId, "order-after-release", T0, T2, 100.0, "LITRE"))
                .isSuccess()).isTrue();
    }

    @Test
    void releasingAnUnknownReferenceIsANotFound() {
        Result<?, ApplicationError> result = fleetReservations.release("order-never-reserved");

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<?, ApplicationError>) result).error().code())
                .isEqualTo("FLEETRESERVATION_NOT_FOUND");
    }

    @Test
    void expiresOverdueReservationsIdempotently() {
        long providerId = 1L;
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0, "LITRE");
        var end = Instant.now().minusSeconds(3600);
        var start = end.minusSeconds(3600);
        assertThat(fleetReservations.reserve(
                reserveRef(providerId, driverId, tankerId, "overdue-1", start, end, 100.0, "LITRE"))
                .isSuccess()).isTrue();

        assertThat(fleetReservations.expireOverdue().getOrElse(-1)).isEqualTo(1);
        assertThat(reservationRepository.findByReference("overdue-1").orElseThrow().getStatus())
                .isEqualTo(FleetReservationStatus.EXPIRED);
        // Idempotent: nothing left to expire.
        assertThat(fleetReservations.expireOverdue().getOrElse(-1)).isZero();
    }

    private static ReserveFleetCommand reserveRef(long providerId, long driverId, long tankerId, String reference,
                                                  Instant start, Instant end, double volume, String unit) {
        return new ReserveFleetCommand(providerId, driverId, tankerId, reference, start, end, volume, unit);
    }
}
