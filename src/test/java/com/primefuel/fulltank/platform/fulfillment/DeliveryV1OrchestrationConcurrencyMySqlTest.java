package com.primefuel.fulltank.platform.fulfillment;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fulfillment.application.commandservices.DeliveryCommandService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AttachReplenishmentOrderCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.supply.api.SupplyReservations;
import com.primefuel.fulltank.platform.supply.domain.model.commands.ReserveSupplyCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T15-B on MySQL 8.0.46: two v1 {@code POST /deliveries} creates for the same driver + tanker must produce
 * exactly one delivery, because the rerouted v1 path goes through the same exclusive-reservation orchestrator
 * v2 uses. Gated so {@code mvnw test} stays hermetic: set {@code ASSIGN_MYSQL_IT=1} plus
 * {@code MYSQL_HOST/PORT/USER/PASSWORD}.
 */
@EnabledIfEnvironmentVariable(named = "ASSIGN_MYSQL_IT", matches = "1")
@SpringBootTest(properties = {"spring.profiles.active=test"})
class DeliveryV1OrchestrationConcurrencyMySqlTest {

    private static final String RUN = Long.toString(System.currentTimeMillis(), 36);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private DeliveryCommandService deliveryCommandService;

    @Autowired
    private FleetRegistry fleetRegistry;

    @Autowired
    private FuelProductCommandService fuelProductCommandService;

    @Autowired
    private ReplenishmentCommandService replenishmentCommandService;

    @Autowired
    private FuelOrderRepository fuelOrderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private SupplyReservations supplyReservations;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        String host = env("MYSQL_HOST", "127.0.0.1");
        String port = env("MYSQL_PORT", "3306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + host + ":" + port + "/fulltank_t15b_v1_assignment"
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
        return 800L + SEQUENCE.incrementAndGet();
    }

    @Test
    void aV1RaceForTheSameResourceProducesExactlyOneDeliveryOnMysql() throws Exception {
        long providerId = providerId();
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel mysql v1 " + RUN, FuelType.DIESEL, 10.0, "GALLONS", 100000.0, 100000.0, providerId, true));
        assertThat(product.isSuccess()).isTrue();
        long productId = product.getOrElse(null).getId();
        var driver = fleetRegistry.registerDriver(new RegisterDriverCommand(providerId, null, "Mysql", "V1",
                "L-MYV1-" + RUN, "999000888", "mysql-v1@example.test", "AVAILABLE"));
        var tanker = fleetRegistry.registerTanker(new RegisterTankerCommand(providerId, "MV1" + RUN,
                "Volvo", "FH", 1000.0, "GALLONS", "AVAILABLE"));
        assertThat(driver.isSuccess() && tanker.isSuccess()).isTrue();
        long driverId = driver.getOrElse(null).id();
        long tankerId = tanker.getOrElse(null).id();

        long orderA = order(providerId, productId);
        long orderB = order(providerId, productId);
        acceptedRequest(providerId, productId, orderA);
        acceptedRequest(providerId, productId, orderB);

        var warmup = "warmup-mysql-v1-" + RUN;
        assertThat(supplyReservations.reserve(
                new ReserveSupplyCommand(providerId, productId, warmup, 1.0, "GALLONS")).isSuccess()).isTrue();
        supplyReservations.release(warmup);

        var barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Result<Delivery, ApplicationError>> results;
        try {
            Future<Result<Delivery, ApplicationError>> first = pool.submit(() -> {
                barrier.await(15, TimeUnit.SECONDS);
                return create(orderA, providerId, driverId, tankerId);
            });
            Future<Result<Delivery, ApplicationError>> second = pool.submit(() -> {
                barrier.await(15, TimeUnit.SECONDS);
                return create(orderB, providerId, driverId, tankerId);
            });
            results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(results.stream().filter(Result::isSuccess).count())
                .as("exactly one v1 create wins the resource race on MySQL").isEqualTo(1);
        assertThat(deliveryRepository.findByProviderId(providerId)).hasSize(1);
    }

    private Result<Delivery, ApplicationError> create(long orderId, long providerId, long driverId, long tankerId) {
        return deliveryCommandService.handle(new CreateDeliveryCommand(orderId, providerId, driverId, tankerId,
                "2026-10-01", "mysql v1 race"));
    }

    private long order(long providerId, long productId) {
        var order = new FuelOrder(new CreateFuelOrderCommand(900L + SEQUENCE.incrementAndGet(), providerId,
                productId, null, 100.0, "Av. Mysql V1 1", LocalDate.parse("2026-10-15")), 1000.0);
        return fuelOrderRepository.save(order).getId();
    }

    private void acceptedRequest(long providerId, long productId, long orderId) {
        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                900L + SEQUENCE.incrementAndGet(), null, null, providerId, productId, 100.0, "GALLONS",
                ReplenishmentSource.MANUAL, "v1-mysql-" + RUN + "-" + SEQUENCE.incrementAndGet()));
        assertThat(created.isSuccess()).isTrue();
        var requestId = created.getOrElse(null).getId();
        assertThat(replenishmentCommandService.handle(new AcceptReplenishmentRequestCommand(requestId)).isSuccess())
                .isTrue();
        assertThat(replenishmentCommandService.handle(
                new AttachReplenishmentOrderCommand(requestId, orderId)).isSuccess()).isTrue();
    }
}
