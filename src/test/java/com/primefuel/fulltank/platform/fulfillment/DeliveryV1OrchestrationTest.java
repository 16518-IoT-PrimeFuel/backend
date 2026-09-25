package com.primefuel.fulltank.platform.fulfillment;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.repositories.DriverRepository;
import com.primefuel.fulltank.platform.fulfillment.application.commandservices.DeliveryCommandService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryStatus;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;
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
import com.primefuel.fulltank.platform.supply.domain.model.valueobjects.ReservationStatus;
import com.primefuel.fulltank.platform.supply.domain.repositories.SupplyReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
 * T15-B: after routing the v1 {@code POST /deliveries} create through the composition root, the legacy path
 * inherits the orchestrator's guarantees — idempotent retry and a race that yields exactly one delivery —
 * while a direct order (no accepted request) keeps its original side effects. It also proves the rollback
 * guarantee reaches v1 (a mid-flight fleet failure leaves no consumed acceptance, no orphan hold, no
 * delivery).
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:delivery_v1_orchestration;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class DeliveryV1OrchestrationTest {

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
    private DriverRepository driverRepository;

    @Autowired
    private FuelProductRepository fuelProductRepository;

    @Autowired
    private SupplyReservations supplyReservations;

    @Autowired
    private SupplyReservationRepository supplyReservationRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    private long providerId() {
        return 300L + SEQUENCE.incrementAndGet();
    }

    private long product(long providerId, double stock) {
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel v1 orch " + SEQUENCE.incrementAndGet(), FuelType.DIESEL, 10.0, "GALLONS", stock, stock,
                providerId, true));
        assertThat(product.isSuccess()).isTrue();
        return product.getOrElse(null).getId();
    }

    private long driver(long providerId) {
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(providerId, null, "V1", "Driver",
                "L-V1ORCH-" + SEQUENCE.incrementAndGet(), "999000777", "v1-orch@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long tanker(long providerId, double capacity) {
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(providerId,
                "V1O-" + SEQUENCE.incrementAndGet(), "Volvo", "FH", capacity, "GALLONS", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long order(long providerId, long productId, double quantity) {
        var order = new FuelOrder(new CreateFuelOrderCommand(900L + SEQUENCE.incrementAndGet(), providerId,
                productId, null, quantity, "Av. V1 Orchestration 1", LocalDate.parse("2099-10-15")), quantity * 10);
        return fuelOrderRepository.save(order).getId();
    }

    private long acceptedRequest(long providerId, long productId, double quantity, String unit, long orderId) {
        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                900L + SEQUENCE.incrementAndGet(), null, null, providerId, productId, quantity, unit,
                ReplenishmentSource.MANUAL, "v1-orch-" + SEQUENCE.incrementAndGet()));
        assertThat(created.isSuccess()).isTrue();
        var requestId = created.getOrElse(null).getId();
        assertThat(replenishmentCommandService.handle(new AcceptReplenishmentRequestCommand(requestId)).isSuccess())
                .isTrue();
        assertThat(replenishmentCommandService.handle(
                new AttachReplenishmentOrderCommand(requestId, orderId)).isSuccess()).isTrue();
        return requestId;
    }

    private Result<com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery, ApplicationError>
    create(long orderId, long providerId, long driverId, long tankerId) {
        return deliveryCommandService.handle(new CreateDeliveryCommand(orderId, providerId, driverId, tankerId,
                "2026-10-01", "v1 orchestration"));
    }

    private static String reference(long orderId) {
        return "delivery-create:" + orderId;
    }

    @Test
    void aDirectOrderKeepsTheLegacyCreateSideEffects() {
        long providerId = providerId();
        long productId = product(providerId, 500.0);
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 2000.0);
        long orderId = order(providerId, productId, 5.0);

        var result = create(orderId, providerId, driverId, tankerId);

        assertThat(result.isSuccess()).isTrue();
        var delivery = deliveryRepository.findById(result.getOrElse(null).getId()).orElseThrow();
        // Legacy semantics: DISPATCHED, no physical_state persisted (derived on read).
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DISPATCHED);
        assertThat(delivery.getPhysicalState()).isNull();
        assertThat(delivery.currentPhysicalState()).isEqualTo(DeliveryPhysicalState.ASSIGNED);
        // Legacy side effects preserved.
        assertThat(fuelOrderRepository.findById(orderId).orElseThrow().getStatus().name()).isEqualTo("DISPATCHED");
        assertThat(driverRepository.findById(driverId).orElseThrow().getStatus()).isEqualTo("ASSIGNED");
        assertThat(fuelProductRepository.findById(productId).orElseThrow().getAvailableStock()).isEqualTo(495.0);
        // The direct-order branch does not reserve supply.
        assertThat(supplyReservationRepository.findByReferenceAndStatus(reference(orderId), ReservationStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void retryingTheSameV1CreateDoesNotDuplicate() {
        long providerId = providerId();
        long productId = product(providerId, 1000.0);
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0);
        long orderId = order(providerId, productId, 100.0);
        acceptedRequest(providerId, productId, 100.0, "GALLONS", orderId);

        var first = create(orderId, providerId, driverId, tankerId);
        var retry = create(orderId, providerId, driverId, tankerId);

        assertThat(first.isSuccess()).isTrue();
        assertThat(retry.isSuccess()).isTrue();
        assertThat(retry.getOrElse(null).getId()).isEqualTo(first.getOrElse(null).getId());
        assertThat(deliveryRepository.findByProviderId(providerId)).hasSize(1);
        assertThat(supplyReservationRepository.findByReferenceAndStatus(reference(orderId), ReservationStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    void aV1RaceForTheSameResourceProducesExactlyOneDelivery() throws Exception {
        long providerId = providerId();
        long productId = product(providerId, 100000.0);
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0);
        long orderA = order(providerId, productId, 100.0);
        long orderB = order(providerId, productId, 100.0);
        acceptedRequest(providerId, productId, 100.0, "GALLONS", orderA);
        acceptedRequest(providerId, productId, 100.0, "GALLONS", orderB);

        var warmup = "warmup-v1-" + SEQUENCE.incrementAndGet();
        assertThat(supplyReservations.reserve(
                new ReserveSupplyCommand(providerId, productId, warmup, 1.0, "GALLONS")).isSuccess()).isTrue();
        supplyReservations.release(warmup);

        var barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Result<com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery, ApplicationError>>
                results;
        try {
            Future<Result<com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery,
                    ApplicationError>> first = pool.submit(() -> {
                barrier.await(15, TimeUnit.SECONDS);
                return create(orderA, providerId, driverId, tankerId);
            });
            Future<Result<com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery,
                    ApplicationError>> second = pool.submit(() -> {
                barrier.await(15, TimeUnit.SECONDS);
                return create(orderB, providerId, driverId, tankerId);
            });
            results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(results.stream().filter(Result::isSuccess).count())
                .as("exactly one v1 create wins the resource race").isEqualTo(1);
        assertThat(deliveryRepository.findByProviderId(providerId)).hasSize(1);
    }

    @Test
    void aFleetFailureRollsBackTheWholeV1Create() {
        long providerId = providerId();
        long productId = product(providerId, 1000.0);
        long driverId = driver(providerId);
        // Tanker too small for the requested volume: the fleet reservation must fail mid-flight.
        long tankerId = tanker(providerId, 50.0);
        long orderId = order(providerId, productId, 100.0);
        acceptedRequest(providerId, productId, 100.0, "GALLONS", orderId);

        var result = create(orderId, providerId, driverId, tankerId);

        assertThat(result.isFailure()).isTrue();
        // Zero partial state from v1 either: no delivery, no orphan supply hold, order not dispatched.
        assertThat(deliveryRepository.findByAssignmentCommandId(reference(orderId))).isEmpty();
        assertThat(supplyReservationRepository.findByReferenceAndStatus(reference(orderId), ReservationStatus.ACTIVE))
                .isEmpty();
        assertThat(fuelOrderRepository.findById(orderId).orElseThrow().getStatus().name()).isEqualTo("PENDING");
    }
}
