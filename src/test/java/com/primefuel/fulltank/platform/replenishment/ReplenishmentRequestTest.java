package com.primefuel.fulltank.platform.replenishment;

import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CancelReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConsumeReplenishmentAcceptanceCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.RejectReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentStatus;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
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
        "spring.datasource.url=jdbc:h2:mem:replenishment;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class ReplenishmentRequestTest {

    @Autowired
    private ReplenishmentCommandService commandService;

    @Autowired
    private FuelProductCommandService fuelProductCommandService;

    private Long aProduct() {
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel rep", FuelType.DIESEL, 12.5, "GAL", 1000.0, 1000.0, 7L, true));
        assertThat(product.isSuccess()).isTrue();
        return product.getOrElse(null).getId();
    }

    @Test
    void enforcesSingleTerminalDecisionAndOnceOnlyAcceptance() {
        var productId = aProduct();

        var created = commandService.handle(new CreateReplenishmentRequestCommand(
                1L, 10L, 20L, 7L, productId, 50.0, "GAL", null, null));
        assertThat(created.isSuccess()).isTrue();
        var request = created.getOrElse(null);
        assertThat(request.getStatus()).isEqualTo(ReplenishmentStatus.PENDING);
        assertThat(request.getUnitPrice()).isEqualTo(12.5);

        var accepted = commandService.handle(new AcceptReplenishmentRequestCommand(request.getId()));
        assertThat(accepted.isSuccess()).isTrue();
        assertThat(accepted.getOrElse(null).getStatus()).isEqualTo(ReplenishmentStatus.ACCEPTED);

        var rejectAfterAccept = commandService.handle(
                new RejectReplenishmentRequestCommand(request.getId(), "too late"));
        assertThat(rejectAfterAccept.isFailure()).isTrue();

        var consumeFirst = commandService.handle(new ConsumeReplenishmentAcceptanceCommand(request.getId()));
        assertThat(consumeFirst.isSuccess()).isTrue();
        assertThat(consumeFirst.getOrElse(false)).isTrue();
        var consumeAgain = commandService.handle(new ConsumeReplenishmentAcceptanceCommand(request.getId()));
        assertThat(consumeAgain.getOrElse(true)).isFalse();

        var rejected = commandService.handle(new CreateReplenishmentRequestCommand(
                1L, 10L, 20L, 7L, productId, 30.0, "GAL", null, null));
        assertThat(commandService.handle(new RejectReplenishmentRequestCommand(
                rejected.getOrElse(null).getId(), "no stock")).isSuccess()).isTrue();

        var cancelled = commandService.handle(new CreateReplenishmentRequestCommand(
                1L, 10L, 20L, 7L, productId, 30.0, "GAL", null, null));
        assertThat(commandService.handle(new CancelReplenishmentRequestCommand(
                cancelled.getOrElse(null).getId())).isSuccess()).isTrue();

        var unknownProduct = commandService.handle(new CreateReplenishmentRequestCommand(
                1L, 10L, 20L, 7L, 999999L, 30.0, "GAL", null, null));
        assertThat(unknownProduct.isFailure()).isTrue();
    }

    @Test
    void creationRejectsInactiveProductAsValidationError() {
        var inactive = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel off", FuelType.DIESEL, 12.5, "GAL", 1000.0, 1000.0, 7L, false)).getOrElse(null).getId();

        var result = commandService.handle(new CreateReplenishmentRequestCommand(
                1L, 10L, 20L, 7L, inactive, 50.0, "GAL", null, null));

        assertThat(result.isFailure()).isTrue();
        assertThat(((Result.Failure<?, ApplicationError>) result).error().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void episodeKeyMakesCreationIdempotent() {
        var productId = aProduct();
        var first = commandService.handle(new CreateReplenishmentRequestCommand(
                1L, 10L, 20L, 7L, productId, 40.0, "GAL", null, "episode-1"));
        var second = commandService.handle(new CreateReplenishmentRequestCommand(
                1L, 10L, 20L, 7L, productId, 40.0, "GAL", null, "episode-1"));
        assertThat(first.getOrElse(null).getId()).isEqualTo(second.getOrElse(null).getId());
    }

    @Test
    void concurrentAcceptAndRejectHaveExactlyOneWinner() throws Exception {
        var productId = aProduct();
        var created = commandService.handle(new CreateReplenishmentRequestCommand(
                1L, 10L, 20L, 7L, productId, 60.0, "GAL", null, null));
        var requestId = created.getOrElse(null).getId();

        var startGate = new CountDownLatch(1);
        var successes = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> accept = pool.submit(() -> {
                try {
                    startGate.await();
                    if (commandService.handle(new AcceptReplenishmentRequestCommand(requestId)).isSuccess()) {
                        successes.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // the losing racer must never win
                }
                return null;
            });
            Future<?> reject = pool.submit(() -> {
                try {
                    startGate.await();
                    if (commandService.handle(new RejectReplenishmentRequestCommand(requestId, "race")).isSuccess()) {
                        successes.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // the losing racer must never win
                }
                return null;
            });
            startGate.countDown();
            accept.get();
            reject.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
    }
}
