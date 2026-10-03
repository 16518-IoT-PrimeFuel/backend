package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.transform;

import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.ProviderDeliveryResource;
import com.primefuel.fulltank.platform.iam.api.BuyerCompanyDirectory;
import com.primefuel.fulltank.platform.inventory.api.FuelProductLookup;
import com.primefuel.fulltank.platform.ordering.api.OrderLookup;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;

import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ProviderDeliveryResourceFromDomainAssembler {
    private final FleetCatalog fleet;
    private final OrderLookup orders;
    private final ReplenishmentLookup requests;
    private final TankAssets tanks;
    private final BuyerCompanyDirectory companies;
    private final FuelProductLookup products;

    public ProviderDeliveryResourceFromDomainAssembler(
            FleetCatalog fleet,
            OrderLookup orders,
            ReplenishmentLookup requests,
            TankAssets tanks,
            BuyerCompanyDirectory companies,
            FuelProductLookup products) {
        this.fleet = fleet;
        this.orders = orders;
        this.requests = requests;
        this.tanks = tanks;
        this.companies = companies;
        this.products = products;
    }

    public ProviderDeliveryResource toResourceFromDomain(Delivery d) {
        var order =
                orders.findById(d.getOrderId())
                        .filter(o -> d.getProviderId().equals(o.providerId()))
                        .orElse(null);
        var request =
                requests.findByOrderId(d.getOrderId())
                        .filter(r -> d.getProviderId().equals(r.providerId()))
                        .orElse(null);
        var buyer = order == null ? null : companies.findById(order.companyId()).orElse(null);
        var tank =
                request == null || request.tankId() == null
                        ? null
                        : tanks.findById(request.tankId())
                                .filter(
                                        t ->
                                                request.organizationId().equals(t.organizationId())
                                                        && Objects.equals(
                                                                request.customerAccountId(),
                                                                t.customerAccountId()))
                                .orElse(null);
        var driver =
                fleet.findDriver(d.getDriverId())
                        .filter(v -> d.getProviderId().equals(v.providerId()))
                        .map(
                                v ->
                                        new ProviderDeliveryResource.DriverResource(
                                                v.id(), v.firstName(), v.lastName()))
                        .orElse(null);
        var tanker =
                fleet.findTanker(d.getVehicleId())
                        .filter(v -> d.getProviderId().equals(v.providerId()))
                        .map(
                                v ->
                                        new ProviderDeliveryResource.TankerResource(
                                                v.id(), v.licensePlate()))
                        .orElse(null);
        var window =
                fleet.findReservationWindow(d.getAssignmentCommandId())
                        .filter(w -> d.getProviderId().equals(w.providerId()))
                        .orElse(null);
        var unit =
                request != null
                        ? request.unit()
                        : order == null
                                ? null
                                : products.findById(order.fuelProductId())
                                        .filter(p -> d.getProviderId().equals(p.providerId()))
                                        .map(p -> p.unit())
                                        .orElse(null);
        return new ProviderDeliveryResource(
                d.getId(),
                d.getOrderId(),
                d.getStatus().name(),
                d.currentPhysicalState().name(),
                driver,
                tanker,
                d.getScheduledDate(),
                window == null ? null : window.start(),
                window == null ? null : window.end(),
                buyer == null ? null : buyer.id(),
                buyer == null ? null : buyer.name(),
                request == null ? null : request.customerAccountId(),
                tank == null ? null : tank.siteId(),
                request != null
                        ? request.deliveryAddress()
                        : order == null ? null : order.deliveryAddress(),
                d.getRequestedVolume() != null
                        ? d.getRequestedVolume()
                        : request != null
                                ? Double.valueOf(request.quantity())
                                : order == null ? null : order.requestedQuantity(),
                unit,
                d.getDeliveredVolume());
    }
}
