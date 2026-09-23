package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AttachReplenishmentOrderCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.supply.api.SupplyReservations;
import com.primefuel.fulltank.platform.supply.domain.model.commands.ReserveSupplyCommand;
import com.primefuel.fulltank.platform.supply.domain.model.valueobjects.ReservationStatus;
import com.primefuel.fulltank.platform.supply.domain.repositories.SupplyReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T15-A assignment against MySQL 8.0.46. Two assignments compete for the same driver + tanker over the same
 * window; exactly one must commit, the other must roll back cleanly — no consumed acceptance left behind and
 * no orphan supply hold — with no deadlock. Loading the context also applies {@code V19} and validates the
 * schema with Hibernate {@code validate}, so this doubles as the MySQL schema check for the new column.
 *
 * <p>Gated so {@code mvnw test} stays hermetic: set {@code ASSIGN_MYSQL_IT=1} plus {@code MYSQL_HOST/PORT/
 * USER/PASSWORD} to run against the local MySQL.
 */
@EnabledIfEnvironmentVariable(named = "ASSIGN_MYSQL_IT", matches = "1")
@SpringBootTest(properties = {"spring.profiles.active=test"})
class AssignDeliveryFlowConcurrencyMySqlTest {

    private static final String RUN = Long.toString(System.currentTimeMillis(), 36);

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T09:00:00Z");

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private AssignDeliveryFlow assignDeliveryFlow;

    @Autowired
    private FleetRegistry fleetRegistry;

    @Autowired
    private FuelProductCommandService fuelProductCommandService;

    @Autowired
    private ReplenishmentCommandService replenishmentCommandService;

    @Autowired
    private SupplyReservations supplyReservations;

    @Autowired
    private SupplyReservationRepository supplyReservationRepository;

    @Autowired
    private ReplenishmentRequestRepository replenishmentRequestRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        String host = env("MYSQL_HOST", "127.0.0.1");
        String port = env("MYSQL_PORT", "3306");
        String database = "fulltank_t15a_assignment";
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

    private long providerId() {
        return 700L + SEQUENCE.incrementAndGet();
    }

    private long product(long providerId) {
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel mysql assign " + RUN + "-" + SEQUENCE.incrementAndGet(), FuelType.DIESEL, 10.0, "GAL",
                100000.0, 100000.0, providerId, true));
        assertThat(product.isSuccess()).isTrue();
        return product.getOrElse(null).getId();
    }

    private long driver(long providerId) {
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(providerId, null, "Mysql", "Assign",
                "L-MYASSIGN-" + RUN + "-" + SEQUENCE.incrementAndGet(), "999000666",
                "mysql-assign@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long tanker(long providerId) {
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(providerId,
                "MA" + RUN + SEQUENCE.incrementAndGet(), "Volvo", "FH", 1000.0, "LITRE", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long acceptedRequest(long providerId, long productId, long orderId) {
        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                950L + SEQUENCE.incrementAndGet(), null, null, providerId, productId, 100.0, "LITRE",
                ReplenishmentSource.MANUAL, "assign-mysql-" + RUN + "-" + SEQUENCE.incrementAndGet()));
        assertThat(created.isSuccess()).isTrue();
        var requestId = created.getOrElse(null).getId();
        assertThat(replenishmentCommandService.handle(new AcceptReplenishmentRequestCommand(requestId)).isSuccess())
                .isTrue();
        assertThat(replenishmentCommandService.handle(
                new AttachReplenishmentOrderCommand(requestId, orderId)).isSuccess()).isTrue();
        return requestId;
    }

    @Test
    void aRaceForTheSameResourceProducesExactlyOneAssignmentOnMysql() throws Exception {
        long providerId = providerId();
        long productId = product(providerId);
        long driverId = driver(providerId);
        long tankerId = tanker(providerId);
        long orderA = 8000L + SEQUENCE.incrementAndGet();
        long orderB = 8000L + SEQUENCE.incrementAndGet();
        long requestA = acceptedRequest(providerId, productId, orderA);
        long requestB = acceptedRequest(providerId, productId, orderB);

        // Pre-create the per-product mutex row so the race is about exclusivity, not lock-row creation.
        var warmup = "warmup-mysql-" + RUN;
        assertThat(supplyReservations.reserve(
                new ReserveSupplyCommand(providerId, productId, warmup, 1.0, "LITRE")).isSuccess()).isTrue();
        supplyReservations.release(warmup);

        var barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Result<AssignDeliveryResult, ApplicationError>> results;
        try {
            Future<Result<AssignDeliveryResult, ApplicationError>> first = pool.submit(() -> {
                barrier.await(15, TimeUnit.SECONDS);
                return assignDeliveryFlow.assign(new AssignDeliveryFlowCommand(
                        "assign-mysql-a-" + RUN, orderA, providerId, driverId, tankerId, T0, T1, "2026-10-01", null));
            });
            Future<Result<AssignDeliveryResult, ApplicationError>> second = pool.submit(() -> {
                barrier.await(15, TimeUnit.SECONDS);
                return assignDeliveryFlow.assign(new AssignDeliveryFlowCommand(
                        "assign-mysql-b-" + RUN, orderB, providerId, driverId, tankerId, T0, T1, "2026-10-01", null));
            });
            results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(results.stream().filter(Result::isSuccess).count())
                .as("exactly one assignment wins on MySQL").isEqualTo(1);
        assertThat(deliveryRepository.findByProviderId(providerId)).hasSize(1);
        long consumed = (consumed(requestA) ? 1 : 0) + (consumed(requestB) ? 1 : 0);
        assertThat(consumed).as("the loser's acceptance rolled back").isEqualTo(1);
        // The loser left no orphan supply hold for its command id.
        assertThat(supplyReservationRepository.findByReferenceAndStatus("assign-mysql-b-" + RUN,
                ReservationStatus.ACTIVE)).isEmpty();
    }

    private boolean consumed(long requestId) {
        return replenishmentRequestRepository.findById(requestId).orElseThrow().isAcceptanceConsumed();
    }
}
