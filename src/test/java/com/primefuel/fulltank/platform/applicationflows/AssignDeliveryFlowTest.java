package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.repositories.FleetReservationRepository;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
 * T15-A orchestration on H2: a happy assignment, commandId idempotency, and — the invariant that matters —
 * failure injection at each step proving no earlier step survives. Failures are injected through real data
 * (a zero-stock product, an undersized tanker) rather than mocks, so the rollback being exercised is the
 * production one.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:assignment_flow;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class AssignDeliveryFlowTest {

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
    private FleetReservationRepository fleetReservationRepository;

    @Autowired
    private ReplenishmentRequestRepository replenishmentRequestRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    private long providerId() {
        return 100L + SEQUENCE.incrementAndGet();
    }

    private long product(long providerId, double stock) {
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel assign " + SEQUENCE.incrementAndGet(), FuelType.DIESEL, 10.0, "GAL", stock, stock,
                providerId, true));
        assertThat(product.isSuccess()).isTrue();
        return product.getOrElse(null).getId();
    }

    private long driver(long providerId) {
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(providerId, null, "Assign", "Driver",
                "L-ASSIGN-" + SEQUENCE.incrementAndGet(), "999000555", "assign-driver@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long tanker(long providerId, double capacity) {
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(providerId,
                "AS-" + SEQUENCE.incrementAndGet(), "Volvo", "FH", capacity, "LITRE", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    /** Creates an accepted replenishment request (attached to {@code orderId}) for the given provider/product. */
    private long acceptedRequest(long providerId, long productId, double quantity, String unit, long orderId) {
        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                900L + SEQUENCE.incrementAndGet(), null, null, providerId, productId, quantity, unit,
                ReplenishmentSource.MANUAL, "assign-flow-" + SEQUENCE.incrementAndGet()));
        assertThat(created.isSuccess()).isTrue();
        var requestId = created.getOrElse(null).getId();
        assertThat(replenishmentCommandService.handle(new AcceptReplenishmentRequestCommand(requestId)).isSuccess())
                .isTrue();
        assertThat(replenishmentCommandService.handle(
                new AttachReplenishmentOrderCommand(requestId, orderId)).isSuccess()).isTrue();
        return requestId;
    }

    private static AssignDeliveryFlowCommand command(String commandId, long orderId, long providerId,
                                                     long driverId, long tankerId) {
        return new AssignDeliveryFlowCommand(commandId, orderId, providerId, driverId, tankerId,
                T0, T1, "2026-10-01", "assign test");
    }

    private boolean consumed(long requestId) {
        return replenishmentRequestRepository.findById(requestId).orElseThrow().isAcceptanceConsumed();
    }

    @Test
    void assignsAnAcceptedOrderHoldingSupplyAndFleet() {
        long providerId = providerId();
        long productId = product(providerId, 1000.0);
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0);
        long orderId = 5000L + SEQUENCE.incrementAndGet();
        long requestId = acceptedRequest(providerId, productId, 100.0, "LITRE", orderId);
        var commandId = "cmd-happy-" + SEQUENCE.incrementAndGet();

        var result = assignDeliveryFlow.assign(command(commandId, orderId, providerId, driverId, tankerId));

        assertThat(result.isSuccess()).isTrue();
        var assignment = result.getOrElse(null);
        assertThat(assignment.deliveryId()).isNotNull();
        assertThat(assignment.supplyReservationId()).isNotNull();
        assertThat(assignment.fleetReservationId()).isNotNull();
        assertThat(assignment.physicalState()).isEqualTo("ASSIGNED");

        var delivery = deliveryRepository.findById(assignment.deliveryId()).orElseThrow();
        assertThat(delivery.currentPhysicalState()).isEqualTo(DeliveryPhysicalState.ASSIGNED);
        assertThat(delivery.getAssignmentCommandId()).isEqualTo(commandId);
        assertThat(consumed(requestId)).isTrue();
        assertThat(supplyReservationRepository.findByReferenceAndStatus(commandId, ReservationStatus.ACTIVE))
                .hasSize(1);
        assertThat(fleetReservationRepository.findByReference(commandId)).isPresent();
    }

    @Test
    void retryingTheSameCommandIsANoOpThatReturnsTheSameDelivery() {
        long providerId = providerId();
        long productId = product(providerId, 1000.0);
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0);
        long orderId = 5000L + SEQUENCE.incrementAndGet();
        long requestId = acceptedRequest(providerId, productId, 100.0, "LITRE", orderId);
        var commandId = "cmd-retry-" + SEQUENCE.incrementAndGet();

        var first = assignDeliveryFlow.assign(command(commandId, orderId, providerId, driverId, tankerId));
        var retry = assignDeliveryFlow.assign(command(commandId, orderId, providerId, driverId, tankerId));

        assertThat(first.isSuccess()).isTrue();
        assertThat(retry.isSuccess()).isTrue();
        assertThat(retry.getOrElse(null).deliveryId()).isEqualTo(first.getOrElse(null).deliveryId());
        // No duplicate reservations or deliveries were created by the retry.
        assertThat(supplyReservationRepository.findByReferenceAndStatus(commandId, ReservationStatus.ACTIVE))
                .hasSize(1);
        assertThat(deliveryRepository.findByAssignmentCommandId(commandId)).isPresent();
        assertThat(consumed(requestId)).isTrue();
    }

    @Test
    void refusesAnOrderWithoutAnAcceptedRequest() {
        long providerId = providerId();
        long productId = product(providerId, 1000.0);
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0);
        long orderId = 5000L + SEQUENCE.incrementAndGet();
        var commandId = "cmd-nonaccept-" + SEQUENCE.incrementAndGet();
        // A pending request is never correlated to an order (only acceptance attaches one), so the order has
        // no accepted request behind it and cannot enter the assignment flow.
        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                900L + SEQUENCE.incrementAndGet(), null, null, providerId, productId, 100.0, "LITRE",
                ReplenishmentSource.MANUAL, "assign-flow-" + SEQUENCE.incrementAndGet()));
        assertThat(created.isSuccess()).isTrue();
        var requestId = created.getOrElse(null).getId();

        var result = assignDeliveryFlow.assign(command(commandId, orderId, providerId, driverId, tankerId));

        assertThat(result.isFailure()).isTrue();
        assertThat(errorCode(result)).isEqualTo("REPLENISHMENTREQUEST_NOT_FOUND");
        assertThat(consumed(requestId)).isFalse();
        assertThat(deliveryRepository.findByAssignmentCommandId(commandId)).isEmpty();
    }

    @Test
    void aSupplyFailureLeavesTheAcceptanceUnconsumedAndNoDelivery() {
        long providerId = providerId();
        // Stock far below the requested quantity: the supply reservation must fail.
        long productId = product(providerId, 10.0);
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0);
        long orderId = 5000L + SEQUENCE.incrementAndGet();
        long requestId = acceptedRequest(providerId, productId, 100.0, "LITRE", orderId);
        var commandId = "cmd-supplyfail-" + SEQUENCE.incrementAndGet();

        var result = assignDeliveryFlow.assign(command(commandId, orderId, providerId, driverId, tankerId));

        assertThat(result.isFailure()).isTrue();
        // Step 1 (consume) was rolled back; steps 3 and 4 never ran.
        assertThat(consumed(requestId)).isFalse();
        assertThat(fleetReservationRepository.findByReference(commandId)).isEmpty();
        assertThat(deliveryRepository.findByAssignmentCommandId(commandId)).isEmpty();
    }

    @Test
    void aFleetFailureRollsBackTheConsumedAcceptanceAndTheSupplyHold() {
        long providerId = providerId();
        long productId = product(providerId, 1000.0);
        long driverId = driver(providerId);
        // Tanker too small for the requested volume: the fleet reservation must fail.
        long tankerId = tanker(providerId, 50.0);
        long orderId = 5000L + SEQUENCE.incrementAndGet();
        long requestId = acceptedRequest(providerId, productId, 100.0, "LITRE", orderId);
        var commandId = "cmd-fleetfail-" + SEQUENCE.incrementAndGet();

        var result = assignDeliveryFlow.assign(command(commandId, orderId, providerId, driverId, tankerId));

        assertThat(result.isFailure()).isTrue();
        // Zero partial state: acceptance not consumed, the supply hold was rolled back, no delivery.
        assertThat(consumed(requestId)).isFalse();
        assertThat(supplyReservationRepository.findByReferenceAndStatus(commandId, ReservationStatus.ACTIVE))
                .isEmpty();
        assertThat(deliveryRepository.findByAssignmentCommandId(commandId)).isEmpty();
    }

    @Test
    void aRaceForTheSameResourceProducesExactlyOneAssignment() throws Exception {
        long providerId = providerId();
        long productId = product(providerId, 100000.0);
        long driverId = driver(providerId);
        long tankerId = tanker(providerId, 1000.0);
        long orderA = 5000L + SEQUENCE.incrementAndGet();
        long orderB = 5000L + SEQUENCE.incrementAndGet();
        long requestA = acceptedRequest(providerId, productId, 100.0, "LITRE", orderA);
        long requestB = acceptedRequest(providerId, productId, 100.0, "LITRE", orderB);

        // Pre-create the per-product supply mutex row (committed) so the race is about resource exclusivity,
        // not about two transactions racing to create that row.
        var warmup = "warmup-" + SEQUENCE.incrementAndGet();
        assertThat(supplyReservations.reserve(
                new ReserveSupplyCommand(providerId, productId, warmup, 1.0, "LITRE")).isSuccess()).isTrue();
        supplyReservations.release(warmup);

        var barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Result<AssignDeliveryResult, ApplicationError>> first = pool.submit(() -> {
                barrier.await(15, TimeUnit.SECONDS);
                return assignDeliveryFlow.assign(command("cmd-race-a-" + SEQUENCE.incrementAndGet(), orderA,
                        providerId, driverId, tankerId));
            });
            Future<Result<AssignDeliveryResult, ApplicationError>> second = pool.submit(() -> {
                barrier.await(15, TimeUnit.SECONDS);
                return assignDeliveryFlow.assign(command("cmd-race-b-" + SEQUENCE.incrementAndGet(), orderB,
                        providerId, driverId, tankerId));
            });
            List<Result<AssignDeliveryResult, ApplicationError>> results =
                    List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(results.stream().filter(Result::isSuccess).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // Exactly one assignment exists; the loser left no orphan (its acceptance is still unconsumed OR its
        // request was assigned, but only one delivery/reservation set exists for the shared resources).
        List<Delivery> deliveries = deliveryRepository.findByProviderId(providerId);
        assertThat(deliveries).hasSize(1);
        long consumedRequests = (consumed(requestA) ? 1 : 0) + (consumed(requestB) ? 1 : 0);
        assertThat(consumedRequests).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static String errorCode(Result<?, ApplicationError> result) {
        return ((Result.Failure<?, ApplicationError>) result).error().code();
    }
}
