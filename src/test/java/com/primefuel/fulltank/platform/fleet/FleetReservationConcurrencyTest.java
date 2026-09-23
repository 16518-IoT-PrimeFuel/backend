package com.primefuel.fulltank.platform.fleet;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.api.FleetReservations;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.ReserveFleetCommand;
import com.primefuel.fulltank.platform.fleet.domain.repositories.FleetReservationRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T13-B concurrency smoke test on H2. Two threads meet at a barrier and issue the reserve call together, so
 * the {@code PESSIMISTIC_WRITE} lock on the shared driver row serialises them: the second waits for the
 * first to commit, then observes the hold and answers {@code 409}. This proves the code path resolves a race
 * to one winner; the real MySQL {@code InnoDB} lock semantics are proved in
 * {@link FleetReservationConcurrencyMySqlTest}.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:fleet_concurrency;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class FleetReservationConcurrencyTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T09:00:00Z");

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
                providerId, null, "Race", "Driver", "L-RACE-" + sequence, "999000333",
                "race-driver-" + sequence + "@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long tanker(long providerId) {
        int sequence = SEQUENCE.incrementAndGet();
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(
                providerId, "RACE-" + sequence, "Volvo", "FH", 1000.0, "LITRE", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    @Test
    void onlyOneOfTwoOverlappingReservationsWinsAndNothingIsCorrupted() throws Exception {
        long providerId = 1L;
        long driverId = driver(providerId);
        long tankerId = tanker(providerId);

        var results = raceForTheSameWindow(providerId, driverId, tankerId, "race-h2");

        long successes = results.stream().filter(Result::isSuccess).count();
        long conflicts = results.stream().filter(Result::isFailure)
                .filter(result -> ((Result.Failure<?, ApplicationError>) result).error().code()
                        .equals("FLEETRESERVATION_CONFLICT"))
                .count();
        assertThat(successes).as("exactly one racer wins").isEqualTo(1);
        assertThat(conflicts).as("the loser gets a clean conflict, not a crash").isEqualTo(1);
        // No half-written or duplicate hold survived the race.
        assertThat(reservationRepository.findActiveByProvider(providerId).stream()
                .filter(reservation -> driverId == reservation.getDriverId()).count()).isEqualTo(1);
    }

    private List<Result<FleetReservations.ReservationSnapshot, ApplicationError>> raceForTheSameWindow(
            long providerId, long driverId, long tankerId, String prefix) throws Exception {
        int workers = 2;
        var barrier = new CyclicBarrier(workers);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Result<FleetReservations.ReservationSnapshot, ApplicationError>>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    barrier.await(15, TimeUnit.SECONDS);
                    return fleetReservations.reserve(new ReserveFleetCommand(providerId, driverId, tankerId,
                            prefix + "-" + index, T0, T1, 100.0, "LITRE"));
                }));
            }
            var results = new ArrayList<Result<FleetReservations.ReservationSnapshot, ApplicationError>>();
            for (var future : futures) {
                // A deadlock would surface here as a timeout rather than a resolved race.
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
