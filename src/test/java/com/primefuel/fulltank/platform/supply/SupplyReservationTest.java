package com.primefuel.fulltank.platform.supply;

import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.supply.application.commandservices.SupplyReservationService;
import com.primefuel.fulltank.platform.supply.domain.model.commands.ReserveSupplyCommand;
import com.primefuel.fulltank.platform.supply.domain.repositories.SupplyReservationRepository;
import com.primefuel.fulltank.platform.supply.domain.model.valueobjects.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:supply_reservations;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class SupplyReservationTest {

    @Autowired
    private SupplyReservationService reservationService;

    @Autowired
    private SupplyReservationRepository reservationRepository;

    @Autowired
    private FuelProductCommandService fuelProductCommandService;

    private Long newProduct(String name, double stock, Long providerId) {
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                name, FuelType.DIESEL, 10.0, "GAL", stock, stock, providerId, true));
        assertThat(product.isSuccess()).isTrue();
        return product.getOrElse(null).getId();
    }

    @Test
    void doesNotOversellAndReleaseIsIdempotent() {
        var productId = newProduct("Diesel reserva", 100.0, 7L);

        var first = reservationService.reserve(new ReserveSupplyCommand(7L, productId, "order-1", 30.0, "GAL"));
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getOrElse(null).getUnitPrice()).isEqualTo(10.0);

        var over = reservationService.reserve(new ReserveSupplyCommand(7L, productId, "order-2", 80.0, "GAL"));
        assertThat(over.isFailure()).isTrue();

        var second = reservationService.reserve(new ReserveSupplyCommand(7L, productId, "order-2", 70.0, "GAL"));
        assertThat(second.isSuccess()).isTrue();

        var release = reservationService.release("order-1");
        assertThat(release.isSuccess()).isTrue();
        assertThat(release.getOrElse(0L)).isEqualTo(1L);

        var releaseAgain = reservationService.release("order-1");
        assertThat(releaseAgain.isSuccess()).isTrue();
        assertThat(releaseAgain.getOrElse(-1L)).isZero();

        assertThat(reservationRepository.findByReferenceAndStatus("order-1", ReservationStatus.ACTIVE)).isEmpty();

        var unknownProduct = reservationService.reserve(new ReserveSupplyCommand(9L, productId, "order-3", 1.0, "GAL"));
        assertThat(unknownProduct.isFailure()).isTrue();
    }

    @Test
    void concurrentReservationsCannotOversell() throws Exception {
        var productId = newProduct("Diesel carrera", 10.0, 8L);

        // Pre-create the per-product mutex row (committed) so the race is about stock, not row creation.
        var warmup = reservationService.reserve(new ReserveSupplyCommand(8L, productId, "warmup", 1.0, "GAL"));
        assertThat(warmup.isSuccess()).isTrue();
        reservationService.release("warmup");

        int workers = 2;
        var startGate = new CountDownLatch(1);
        var successes = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            Future<?>[] futures = new Future<?>[workers];
            for (int i = 0; i < workers; i++) {
                final int index = i;
                futures[i] = pool.submit(() -> {
                    try {
                        startGate.await();
                        var result = reservationService.reserve(
                                new ReserveSupplyCommand(8L, productId, "race-" + index, 8.0, "GAL"));
                        if (result.isSuccess()) {
                            successes.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        // A losing racer may fail; it must never oversell.
                    }
                    return null;
                });
            }
            startGate.countDown();
            for (var future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        var activeTotal = reservationRepository.findActiveByProduct(8L, productId).stream()
                .mapToDouble(r -> r.getQuantity()).sum();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(activeTotal).isLessThanOrEqualTo(10.0);
    }
}
