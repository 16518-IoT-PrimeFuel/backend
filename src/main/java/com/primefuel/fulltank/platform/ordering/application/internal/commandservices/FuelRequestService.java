package com.primefuel.fulltank.platform.ordering.application.internal.commandservices;

import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;
import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelRequestCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.RequestStatus;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.ordering.application.ports.FuelRequestData;
import com.primefuel.fulltank.platform.ordering.application.ports.FuelRequestStore;
import com.primefuel.fulltank.platform.shared.application.events.DurableEvent;
import com.primefuel.fulltank.platform.shared.application.events.DurableEventPublisher;
import com.primefuel.fulltank.platform.inventory.application.ports.SupplyReservationStore;
import com.primefuel.fulltank.platform.iam.application.ports.UserRecipientLookup;
import com.primefuel.fulltank.platform.ordering.application.ports.ReplenishmentLifecycleStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class FuelRequestService implements com.primefuel.fulltank.platform.ordering.application.ports.AutomaticReplenishmentRequest {
    private final FuelRequestStore requests;
    private final FuelProductRepository products;
    private final FuelOrderRepository orders;
    private final DurableEventPublisher events;
    private final SupplyReservationStore reservations;
    private final UserRecipientLookup recipients;
    private final ReplenishmentLifecycleStore lifecycle;

    public FuelRequestService(FuelRequestStore requests,
                              FuelProductRepository products,
                              FuelOrderRepository orders,
                              DurableEventPublisher events,
                              SupplyReservationStore reservations,
                              UserRecipientLookup recipients,
                              ReplenishmentLifecycleStore lifecycle) {
        this.requests = requests;
        this.products = products;
        this.orders = orders;
        this.events = events;
        this.reservations = reservations;
        this.recipients = recipients;
        this.lifecycle = lifecycle;
    }

    @Transactional
    public FuelRequestData create(CreateFuelRequestCommand command) {
        return create(command, null);
    }

    @Override
    @Transactional
    public FuelRequestData create(CreateFuelRequestCommand command, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existingId = lifecycle.findRequestIdByIdempotencyKey(idempotencyKey);
            if (existingId.isPresent()) return requests.findById(existingId.get()).orElseThrow();
        }
        var product = products.findById(command.fuelProductId())
                .orElseThrow(() -> new IllegalArgumentException("Fuel product not found"));
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalArgumentException("Fuel product is inactive");
        }
        if (!product.getProviderId().equals(command.providerId())) {
            throw new IllegalArgumentException("Fuel product does not belong to provider");
        }
        if (command.quantity() == null || command.quantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (product.getAvailableStock() == null || command.quantity() > product.getAvailableStock()) {
            throw new IllegalArgumentException("Requested quantity exceeds available stock");
        }
        if (command.deliveryDate() == null || command.deliveryDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Delivery date cannot be in the past");
        }
        var request = new FuelRequestData(null, command.buyerCompanyId(), command.providerId(),
                command.equipmentId(), command.fuelProductId(), product.getFuelType().name(), product.getName(),
                command.quantity(), command.unit() == null ? product.getUnit() : command.unit(),
                product.getPricePerUnit(), command.deliveryAddress(), command.deliveryDate(), RequestStatus.PENDING,
                command.source() == null ? "MANUAL" : command.source().toUpperCase(), null, null, null);
        var saved = requests.save(request);
        lifecycle.create(saved.id(), idempotencyKey == null ? "manual:" + saved.id() : idempotencyKey);
        var providerUserId = recipients.findByProviderId(saved.providerId()).orElse(null);
        events.publish(new DurableEvent("fuel-request:" + saved.id() + ":created", "FuelRequestCreated",
                "FuelRequest", saved.id().toString(), payload(providerUserId, "requestId=" + saved.id()), Instant.now()));
        return saved;
    }

    public List<FuelRequestData> findAll(Long buyerCompanyId, Long providerId) {
        if (buyerCompanyId != null) return requests.findByBuyerCompanyId(buyerCompanyId);
        if (providerId != null) return requests.findByProviderId(providerId);
        return requests.findAll();
    }

    public Optional<FuelRequestData> findById(Long requestId) {
        return requests.findById(requestId);
    }

    @Transactional
    public FuelOrder accept(Long requestId) {
        var request = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Fuel request not found"));
        if (request.status() != RequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be accepted");
        }
        if (!lifecycle.transition(requestId, "PENDING", "ACCEPTED")) {
            throw new IllegalStateException("Request was already reviewed");
        }
        var product = products.findById(request.fuelProductId())
                .orElseThrow(() -> new IllegalArgumentException("Fuel product not found"));
        var command = new CreateFuelOrderCommand(request.buyerCompanyId(), request.providerId(),
                request.fuelProductId(), request.equipmentId(), request.quantity(),
                request.deliveryAddress(), request.deliveryDate());
        if (!reservations.reserve(requestId, request.fuelProductId(), request.quantity())) {
            throw new IllegalStateException("Fuel supply is no longer available");
        }
        var order = new FuelOrder(command, product.getPricePerUnit() * request.quantity());
        order.setRequestId(requestId);
        order = orders.save(order);
        requests.save(withStatus(request, RequestStatus.APPROVED, null));
        var buyerUserId = recipients.findByCompanyId(request.buyerCompanyId()).orElse(null);
        events.publish(new DurableEvent("fuel-request:" + requestId + ":approved", "FuelRequestApproved",
                "FuelRequest", requestId.toString(), payload(buyerUserId, "orderId=" + order.getId()), Instant.now()));
        return order;
    }

    @Transactional
    public FuelRequestData reject(Long requestId, String reason) {
        var request = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Fuel request not found"));
        if (request.status() != RequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be rejected");
        }
        if (!lifecycle.transition(requestId, "PENDING", "REJECTED")) {
            throw new IllegalStateException("Request was already reviewed");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        var rejected = requests.save(withStatus(request, RequestStatus.REJECTED, reason.trim()));
        var buyerUserId = recipients.findByCompanyId(request.buyerCompanyId()).orElse(null);
        events.publish(new DurableEvent("fuel-request:" + requestId + ":rejected", "FuelRequestRejected",
                "FuelRequest", requestId.toString(), payload(buyerUserId, "reason=" + reason.trim()), Instant.now()));
        return rejected;
    }

    @Transactional
    public FuelRequestData cancel(Long requestId) {
        var request = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Fuel request not found"));
        if (request.status() != RequestStatus.PENDING || !lifecycle.transition(requestId, "PENDING", "CANCELLED")) {
            throw new IllegalStateException("Only pending requests can be cancelled");
        }
        return requests.save(withStatus(request, RequestStatus.CANCELLED, "Cancelled by buyer"));
    }

    @Transactional
    public boolean consumeAcceptance(Long requestId, Long orderId) {
        return lifecycle.consume(requestId, orderId);
    }

    private static FuelRequestData withStatus(FuelRequestData request, RequestStatus status, String reason) {
        return new FuelRequestData(request.id(), request.buyerCompanyId(), request.providerId(), request.equipmentId(),
                request.fuelProductId(), request.fuelType(), request.productName(), request.quantity(), request.unit(),
                request.unitPrice(), request.deliveryAddress(), request.deliveryDate(), status, request.source(),
                reason, request.createdAt(), request.updatedAt());
    }

    private static String payload(Long userId, String value) {
        return "userId=" + (userId == null ? "" : userId) + ";" + value;
    }
}
