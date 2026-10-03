package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.fleet.api.*;
import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.ordering.api.OrderLookup;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.shared.application.result.*;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.ZoneOffset;
import java.util.Comparator;

@Service
@Transactional(readOnly = true)
public class DeliveryRecommendationQueryService {
    private final OrderLookup orders;
    private final ReplenishmentLookup requests;
    private final EligibilityQuery eligibility;
    private final FleetCatalog fleet;

    public DeliveryRecommendationQueryService(
            OrderLookup orders,
            ReplenishmentLookup requests,
            EligibilityQuery eligibility,
            FleetCatalog fleet) {
        this.orders = orders;
        this.requests = requests;
        this.eligibility = eligibility;
        this.fleet = fleet;
    }

    public Result<DeliveryRecommendation, ApplicationError> handle(DeliveryRecommendationQuery q) {
        var order =
                orders.findById(q.orderId())
                        .filter(o -> q.providerId().equals(o.providerId()))
                        .orElse(null);
        var request =
                requests.findByOrderId(q.orderId())
                        .filter(r -> q.providerId().equals(r.providerId()))
                        .orElse(null);
        if (order == null || request == null)
            return Result.failure(ApplicationError.notFound("Order", String.valueOf(q.orderId())));
        if (!"ACCEPTED".equals(request.status()) || request.acceptanceConsumed())
            return Result.failure(
                    ApplicationError.conflict(
                            "Order", "Request must be accepted and not already assigned"));
        if ((q.windowStart() == null) != (q.windowEnd() == null))
            return Result.failure(
                    ApplicationError.validationError(
                            "window", "Both windowStart and windowEnd are required together"));
        var date = request.deliveryDate() != null ? request.deliveryDate() : order.scheduledDate();
        if (q.windowStart() == null && date == null)
            return Result.failure(
                    ApplicationError.validationError(
                            "window", "Supply a window when the order has no scheduled date"));
        var start =
                q.windowStart() != null
                        ? q.windowStart()
                        : date.atStartOfDay().toInstant(ZoneOffset.UTC);
        var end =
                q.windowEnd() != null
                        ? q.windowEnd()
                        : date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        if (!start.isBefore(end))
            return Result.failure(
                    ApplicationError.validationError(
                            "window", "windowStart must precede windowEnd"));
        double litres = Unit.fromCode(request.unit()).toLitres(request.quantity());
        var drivers =
                eligibility.eligibleDrivers(q.providerId()).stream()
                        .filter(d -> q.providerId().equals(d.providerId()) && d.active())
                        .sorted(Comparator.comparing(FleetCatalog.DriverSnapshot::id))
                        .toList();
        var tankers =
                eligibility.eligibleTankers(q.providerId()).stream()
                        .filter(
                                t ->
                                        q.providerId().equals(t.providerId())
                                                && t.active()
                                                && t.capacity() != null
                                                && Unit.fromCode(t.unit()).toLitres(t.capacity())
                                                        >= litres)
                        .sorted(
                                Comparator.comparingDouble(
                                                (FleetCatalog.TankerSnapshot t) ->
                                                        Unit.fromCode(t.unit())
                                                                .toLitres(t.capacity()))
                                        .thenComparing(FleetCatalog.TankerSnapshot::id))
                        .toList();
        String criterion =
                "Smallest sufficient tanker in litres, then lowest tanker id; lowest eligible"
                        + " driver id; no active reservation overlap";
        for (var tanker : tankers)
            for (var driver : drivers) {
                if (!fleet.hasReservationConflict(
                        q.providerId(), driver.id(), tanker.id(), start, end))
                    return Result.success(
                            new DeliveryRecommendation(
                                    q.orderId(),
                                    true,
                                    null,
                                    driver.id(),
                                    driver.firstName() + " " + driver.lastName(),
                                    tanker.id(),
                                    tanker.licensePlate(),
                                    Unit.fromCode(tanker.unit()).toLitres(tanker.capacity()),
                                    litres,
                                    start,
                                    end,
                                    criterion));
            }
        String reason =
                drivers.isEmpty()
                        ? "NO_ELIGIBLE_DRIVER"
                        : tankers.isEmpty()
                                ? "NO_SUFFICIENT_ELIGIBLE_TANKER"
                                : "RESERVATION_CONFLICT";
        return Result.success(
                new DeliveryRecommendation(
                        q.orderId(),
                        false,
                        reason,
                        null,
                        null,
                        null,
                        null,
                        null,
                        litres,
                        start,
                        end,
                        criterion));
    }
}
