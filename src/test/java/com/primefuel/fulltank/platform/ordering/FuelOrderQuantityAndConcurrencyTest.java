package com.primefuel.fulltank.platform.ordering;

import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.ordering.application.commandservices.FuelOrderCommandService;
import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CancelFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.ConfirmFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.OrderStatus;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AttachReplenishmentOrderCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentStatus;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.EventPublicationPersistenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:orderquantity;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class FuelOrderQuantityAndConcurrencyTest {

    private static final long PROVIDER = 71L;

    @Autowired
    private FuelOrderCommandService orders;
    @Autowired
    private FuelOrderRepository orderRepository;
    @Autowired
    private FuelProductCommandService products;
    @Autowired
    private ReplenishmentCommandService replenishment;
    @Autowired
    private ReplenishmentRequestRepository requestRepository;
    @Autowired
    private EventPublicationPersistenceRepository publications;

    private long product(boolean active) {
        return products.handle(new CreateFuelProductCommand(
                "Diesel ord", FuelType.DIESEL, 10.0, "GAL", 1000.0, 1000.0, PROVIDER, active))
                .getOrElse(null).getId();
    }

    private Result<FuelOrder, ApplicationError> create(long productId, Double quantity, String address, Long requestId) {
        return orders.handle(new CreateFuelOrderCommand(5L, PROVIDER, productId, null, quantity, address,
                LocalDate.now().plusDays(1), requestId));
    }

    private FuelOrder order() {
        return create(product(true), 50.0, "Av. Prueba", null).getOrElse(null);
    }

    private static String code(Result<?, ApplicationError> result) {
        assertThat(result.isFailure()).isTrue();
        return ((Result.Failure<?, ApplicationError>) result).error().code();
    }

    @Test
    void invalidQuantityAddressOrInactiveProductIsAValidationError() {
        var productId = product(true);
        assertThat(code(create(productId, null, "Av. Prueba", null))).isEqualTo("VALIDATION_ERROR");
        assertThat(code(create(productId, 0.0, "Av. Prueba", null))).isEqualTo("VALIDATION_ERROR");
        assertThat(code(create(productId, -3.0, "Av. Prueba", null))).isEqualTo("VALIDATION_ERROR");
        assertThat(code(create(productId, 10.0, "  ", null))).isEqualTo("VALIDATION_ERROR");
        assertThat(code(create(product(false), 10.0, "Av. Prueba", null))).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void staleWriteIsRejectedInsteadOfCorruptingTheState() {
        var id = order().getId();
        var first = orderRepository.findById(id).orElseThrow();
        var second = orderRepository.findById(id).orElseThrow();
        first.confirm();
        orderRepository.save(first);
        second.cancel();

        assertThatThrownBy(() -> orderRepository.save(second)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(orderRepository.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void concurrentConfirmAndCancelLeaveOneCoherentState() throws Exception {
        var id = order().getId();
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var confirm = pool.submit(() -> {
                gate.await();
                try {
                    orders.handle(new ConfirmFuelOrderCommand(id));
                } catch (OptimisticLockingFailureException ignored) {
                    // the loser is rejected (409 at the REST edge)
                }
                return null;
            });
            var cancel = pool.submit(() -> {
                gate.await();
                try {
                    orders.handle(new CancelFuelOrderCommand(id));
                } catch (OptimisticLockingFailureException ignored) {
                    // the loser is rejected (409 at the REST edge)
                }
                return null;
            });
            gate.countDown();
            confirm.get();
            cancel.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(orderRepository.findById(id).orElseThrow().getStatus())
                .isIn(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
    }

    @Test
    void cancellingPublishesAnEventAndCancelsTheAcceptedRequest() {
        var productId = product(true);
        var request = replenishment.handle(new CreateReplenishmentRequestCommand(
                5L, null, 1L, PROVIDER, productId, 10.0, "GAL", null, null, "Av. Prueba", null))
                .getOrElse(null).getId();
        assertThat(replenishment.handle(new AcceptReplenishmentRequestCommand(request)).isSuccess()).isTrue();
        var order = create(productId, 10.0, "Av. Prueba", request).getOrElse(null);
        assertThat(replenishment.handle(new AttachReplenishmentOrderCommand(request, order.getId())).isSuccess())
                .isTrue();

        var cancelled = orders.handle(new CancelFuelOrderCommand(order.getId()));

        assertThat(cancelled.isSuccess()).isTrue();
        assertThat(requestRepository.findById(request).orElseThrow().getStatus())
                .isEqualTo(ReplenishmentStatus.CANCELLED);
        var events = publications.findByAggregateTypeAndAggregateIdOrderByIdAsc(
                "FuelOrder", String.valueOf(order.getId()));
        assertThat(events).extracting(e -> e.getEventType()).containsExactly("order.cancelled.v1");
        // cancelling again is a no-op: no second event
        assertThat(orders.handle(new CancelFuelOrderCommand(order.getId())).isSuccess()).isTrue();
        assertThat(publications.findByAggregateTypeAndAggregateIdOrderByIdAsc(
                "FuelOrder", String.valueOf(order.getId()))).hasSize(1);
    }
}
