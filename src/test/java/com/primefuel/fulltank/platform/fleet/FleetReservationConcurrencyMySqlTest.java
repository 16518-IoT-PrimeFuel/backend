package com.primefuel.fulltank.platform.fleet;

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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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
 * T13-B: the real race against MySQL 8.0.46. Two connections contend for the same driver + tanker with
 * overlapping windows; the {@code PESSIMISTIC_WRITE} locks must serialise them into exactly one winner and
 * one clean {@code 409}, with no deadlock and no corrupted/duplicated hold. H2 is only a smoke test — row
 * locking semantics differ, so this is the one that counts.
 *
 * <p>Gated so the normal {@code mvnw test} (H2) stays hermetic: set {@code FLEET_MYSQL_IT=1} plus
 * {@code MYSQL_HOST}/{@code MYSQL_PORT}/{@code MYSQL_USER}/{@code MYSQL_PASSWORD} to run it against the
 * local MySQL. It creates/reuses a disposable database and validates the Flyway schema on the way up.
 */
@EnabledIfEnvironmentVariable(named = "FLEET_MYSQL_IT", matches = "1")
@SpringBootTest(properties = {"spring.profiles.active=test"})
class FleetReservationConcurrencyMySqlTest {

    private static final String RUN = Long.toString(System.currentTimeMillis(), 36);

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T09:00:00Z");

    @Autowired
    private FleetRegistry fleetRegistry;

    @Autowired
    private FleetReservations fleetReservations;

    @Autowired
    private FleetReservationRepository reservationRepository;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        String host = env("MYSQL_HOST", "127.0.0.1");
        String port = env("MYSQL_PORT", "3306");
        String database = "fulltank_t13b_race";
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> env("MYSQL_USER", "root"));
        registry.add("spring.datasource.password", () -> env("MYSQL_PASSWORD", ""));
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.flyway.baseline-version", () -> "1");
        registry.add("authorization.jwt.secret", () -> "0123456789abcdef0123456789abcdef");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private long driver(long providerId) {
        int sequence = SEQUENCE.incrementAndGet();
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(
                providerId, null, "Race", "Driver", "L-MYSQL-" + RUN + "-" + sequence, "999000444",
                "mysql-driver-" + RUN + "-" + sequence + "@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long tanker(long providerId) {
        int sequence = SEQUENCE.incrementAndGet();
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(
                providerId, "MY" + RUN + sequence, "Volvo", "FH", 1000.0, "LITRE", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    @Test
    void oneReservationWinsTheRaceAndTheOtherFailsCleanly() throws Exception {
        long providerId = 1L;
        long driverId = driver(providerId);
        long tankerId = tanker(providerId);

        var results = raceForTheSameWindow(providerId, driverId, tankerId);

        long successes = results.stream().filter(Result::isSuccess).count();
        long conflicts = results.stream().filter(Result::isFailure)
                .filter(result -> ((Result.Failure<?, ApplicationError>) result).error().code()
                        .equals("FLEETRESERVATION_CONFLICT"))
                .count();
        assertThat(successes).as("exactly one racer wins on MySQL").isEqualTo(1);
        assertThat(conflicts).as("the loser gets a clean conflict, not a deadlock or crash").isEqualTo(1);
        assertThat(reservationRepository.findActiveByProvider(providerId).stream()
                .filter(reservation -> driverId == reservation.getDriverId()).count()).isEqualTo(1);
    }

    @Test
    void releaseAndExpiryAreIdempotentOnMysql() {
        long providerId = 1L;

        long releaseDriverId = driver(providerId);
        long releaseTankerId = tanker(providerId);
        var releaseRef = "mysql-release-" + RUN;
        assertThat(fleetReservations.reserve(new ReserveFleetCommand(providerId, releaseDriverId, releaseTankerId,
                releaseRef, T0, T1, 100.0, "LITRE")).isSuccess()).isTrue();
        assertThat(fleetReservations.release(releaseRef).getOrElse(null).status()).isEqualTo("RELEASED");
        assertThat(fleetReservations.release(releaseRef).getOrElse(null).status()).isEqualTo("RELEASED");

        long expiryDriverId = driver(providerId);
        long expiryTankerId = tanker(providerId);
        var end = Instant.now().minusSeconds(3600);
        var expiryRef = "mysql-expiry-" + RUN;
        assertThat(fleetReservations.reserve(new ReserveFleetCommand(providerId, expiryDriverId, expiryTankerId,
                expiryRef, end.minusSeconds(3600), end, 100.0, "LITRE")).isSuccess()).isTrue();

        assertThat(fleetReservations.expireOverdue().getOrElse(-1)).isGreaterThanOrEqualTo(1);
        assertThat(reservationRepository.findByReference(expiryRef).orElseThrow().getStatus())
                .isEqualTo(FleetReservationStatus.EXPIRED);
        // Idempotent: everything past due was already swept.
        assertThat(fleetReservations.expireOverdue().getOrElse(-1)).isZero();
    }

    private List<Result<FleetReservations.ReservationSnapshot, ApplicationError>> raceForTheSameWindow(
            long providerId, long driverId, long tankerId) throws Exception {
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
                            "mysql-race-" + RUN + "-" + index, T0, T1, 100.0, "LITRE"));
                }));
            }
            var results = new ArrayList<Result<FleetReservations.ReservationSnapshot, ApplicationError>>();
            for (var future : futures) {
                // A deadlock (lock-order inversion) would surface here as a timeout instead of a resolution.
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
