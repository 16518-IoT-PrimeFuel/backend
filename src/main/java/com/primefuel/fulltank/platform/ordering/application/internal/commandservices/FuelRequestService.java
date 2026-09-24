package com.primefuel.fulltank.platform.ordering.application.internal.commandservices;

import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;
import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelRequestCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.RequestStatus;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.ordering.application.ports.FuelRequestData;
import com.primefuel.fulltank.platform.ordering.application.ports.FuelRequestStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FuelRequestService {
    private final FuelRequestStore requests;
    private final FuelProductRepository products;
    private final FuelOrderRepository orders;

    public FuelRequestService(FuelRequestStore requests,
                              FuelProductRepository products,
                              FuelOrderRepository orders) {
        this.requests = requests;
        this.products = products;
        this.orders = orders;
    }

    @Transactional
    public FuelRequestData create(CreateFuelRequestCommand command) {
        var product = products.findById(command.fuelProductId())
                .orElseThrow(() -> new IllegalArgumentException("Fuel product not found"));
        if (!product.getProviderId().equals(command.providerId())) {
            throw new IllegalArgumentException("Fuel product does not belong to provider");
        }
        var request = new FuelRequestData(null, command.buyerCompanyId(), command.providerId(),
                command.equipmentId(), command.fuelProductId(), product.getFuelType().name(), product.getName(),
                command.quantity(), command.unit() == null ? product.getUnit() : command.unit(),
                product.getPricePerUnit(), command.deliveryAddress(), command.deliveryDate(), RequestStatus.PENDING,
                command.source() == null ? "MANUAL" : command.source().toUpperCase(), null, null, null);
        return requests.save(request);
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
        var product = products.findById(request.fuelProductId())
                .orElseThrow(() -> new IllegalArgumentException("Fuel product not found"));
        var command = new CreateFuelOrderCommand(request.buyerCompanyId(), request.providerId(),
                request.fuelProductId(), request.equipmentId(), request.quantity(),
                request.deliveryAddress(), request.deliveryDate());
        var order = new FuelOrder(command, product.getPricePerUnit() * request.quantity());
        order.setRequestId(requestId);
        order = orders.save(order);
        requests.save(withStatus(request, RequestStatus.APPROVED, null));
        return order;
    }

    @Transactional
    public FuelRequestData reject(Long requestId, String reason) {
        var request = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Fuel request not found"));
        if (request.status() != RequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be rejected");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        return requests.save(withStatus(request, RequestStatus.REJECTED, reason.trim()));
    }

    private static FuelRequestData withStatus(FuelRequestData request, RequestStatus status, String reason) {
        return new FuelRequestData(request.id(), request.buyerCompanyId(), request.providerId(), request.equipmentId(),
                request.fuelProductId(), request.fuelType(), request.productName(), request.quantity(), request.unit(),
                request.unitPrice(), request.deliveryAddress(), request.deliveryDate(), status, request.source(),
                reason, request.createdAt(), request.updatedAt());
    }
}
