package com.primefuel.fulltank.platform.ordering;

import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services.CurrentUserAccess;
import com.primefuel.fulltank.platform.ordering.application.internal.commandservices.FuelOrderCommandServiceImpl;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.ReceiveFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.OrderStatus;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.FuelOrdersController;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import com.primefuel.fulltank.platform.equipment.application.queryservices.EquipmentQueryService;
import com.primefuel.fulltank.platform.inventory.application.queryservices.FuelProductQueryService;
import com.primefuel.fulltank.platform.supply.api.SupplyCatalog;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-07: the buyer's receipt settles a dispatched order, is idempotent, and is refused from other states. */
class FuelOrderReceiveTest {

    private static FuelOrder order(OrderStatus status) {
        var order = new FuelOrder();
        order.setId(9L);
        order.setCompanyId(5L);
        order.setStatus(status);
        return order;
    }

    @Test
    void theDomainSettlesADispatchedOrderAndTreatsSettledOrPaidAsNoOp() {
        var dispatched = order(OrderStatus.DISPATCHED);
        dispatched.receive();
        assertThat(dispatched.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        dispatched.receive(); // repeated call: no-op
        assertThat(dispatched.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        var paid = order(OrderStatus.PAID);
        paid.receive();
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void theDomainRefusesReceiptBeforeDispatchOrAfterCancellation() {
        for (var status : new OrderStatus[]{OrderStatus.PENDING, OrderStatus.CONFIRMED,
                OrderStatus.CANCELLED}) {
            assertThatThrownBy(() -> order(status).receive()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void theServiceSavesTheSettledOrderAndMapsRefusalsToConflictOrNotFound() {
        var repository = mock(FuelOrderRepository.class);
        var service = new FuelOrderCommandServiceImpl(repository, mock(FuelProductQueryService.class),
                mock(EquipmentQueryService.class), mock(SupplyCatalog.class), mock(ReplenishmentLookup.class),
                mock(EventPublicationRegistry.class));

        var dispatched = order(OrderStatus.DISPATCHED);
        when(repository.findById(9L)).thenReturn(Optional.of(dispatched));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.handle(new ReceiveFuelOrderCommand(9L));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrElse(null).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        var cancelled = order(OrderStatus.CANCELLED);
        when(repository.findById(10L)).thenReturn(Optional.of(cancelled));
        var conflict = service.handle(new ReceiveFuelOrderCommand(10L));
        assertThat(conflict.isFailure()).isTrue();
        assertThat(((com.primefuel.fulltank.platform.shared.application.result.Result.Failure<?, ?>) conflict)
                .error().toString()).contains("CONFLICT");

        assertThat(service.handle(new ReceiveFuelOrderCommand(404L)).isFailure()).isTrue();
    }

    @Test
    void theEndpointAnswersNotFoundForAnOrderOfAnotherBuyerAndDoesNotTouchTheOrder() {
        var queries = mock(FuelOrderQueryService.class);
        var commands = mock(com.primefuel.fulltank.platform.ordering.application.commandservices.FuelOrderCommandService.class);
        var access = mock(CurrentUserAccess.class);
        var controller = new FuelOrdersController(commands, queries, access);
        when(queries.handle(any(com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrderByIdQuery.class)))
                .thenReturn(Optional.of(order(OrderStatus.DISPATCHED)));
        when(access.ownsCompany(5L)).thenReturn(false);

        assertThatThrownBy(() -> controller.receiveOrder(9L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(commands, never()).handle(any(ReceiveFuelOrderCommand.class));
    }

    @Test
    void theEndpointAnswersNotFoundForAMissingOrder() {
        var queries = mock(FuelOrderQueryService.class);
        var commands = mock(com.primefuel.fulltank.platform.ordering.application.commandservices.FuelOrderCommandService.class);
        var controller = new FuelOrdersController(commands, queries, mock(CurrentUserAccess.class));
        when(queries.handle(any(com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrderByIdQuery.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.receiveOrder(404L))
                .isInstanceOf(ResponseStatusException.class);
    }
}
