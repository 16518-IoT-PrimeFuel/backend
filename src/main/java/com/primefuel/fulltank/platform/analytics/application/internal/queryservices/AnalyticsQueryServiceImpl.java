package com.primefuel.fulltank.platform.analytics.application.internal.queryservices;

import com.primefuel.fulltank.platform.analytics.application.internal.outboundservices.acl.ExternalFulfillmentService;
import com.primefuel.fulltank.platform.analytics.application.internal.outboundservices.acl.ExternalOrderingService;
import com.primefuel.fulltank.platform.analytics.application.internal.outboundservices.acl.ExternalPaymentService;
import com.primefuel.fulltank.platform.analytics.application.queryservices.AnalyticsQueryService;
import com.primefuel.fulltank.platform.analytics.domain.model.queries.GetBuyerAnalyticsQuery;
import com.primefuel.fulltank.platform.analytics.domain.model.queries.GetPlatformSummaryQuery;
import com.primefuel.fulltank.platform.analytics.domain.model.queries.GetProviderAnalyticsQuery;
import com.primefuel.fulltank.platform.analytics.domain.model.valueobjects.BuyerAnalytics;
import com.primefuel.fulltank.platform.analytics.domain.model.valueobjects.MonthlyAmount;
import com.primefuel.fulltank.platform.analytics.domain.model.valueobjects.PlatformSummary;
import com.primefuel.fulltank.platform.analytics.domain.model.valueobjects.ProviderAnalytics;
import com.primefuel.fulltank.platform.analytics.domain.model.valueobjects.SalesTrendPoint;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnalyticsQueryServiceImpl implements AnalyticsQueryService {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String DELIVERED = "DELIVERED";
    private static final String CANCELLED = "CANCELLED";
    private static final String PENDING = "PENDING";
    private static final String COMPLETED = "COMPLETED";

    private final ExternalOrderingService externalOrderingService;
    private final ExternalPaymentService externalPaymentService;
    private final ExternalFulfillmentService externalFulfillmentService;

    public AnalyticsQueryServiceImpl(
            ExternalOrderingService externalOrderingService,
            ExternalPaymentService externalPaymentService,
            ExternalFulfillmentService externalFulfillmentService) {
        this.externalOrderingService = externalOrderingService;
        this.externalPaymentService = externalPaymentService;
        this.externalFulfillmentService = externalFulfillmentService;
    }

    @Override
    public ProviderAnalytics handle(GetProviderAnalyticsQuery query) {
        var allOrders = externalOrderingService.fetchOrdersByProviderId(query.providerId());
        var orders = allOrders.stream().filter(o -> inPeriod(o.createdAt(), query)).toList();
        long confirmed =
                orders.stream()
                        .filter(o -> CONFIRMED.equals(o.status()) || DELIVERED.equals(o.status()))
                        .count();
        long cancelled = orders.stream().filter(o -> CANCELLED.equals(o.status())).count();
        long pending = orders.stream().filter(o -> PENDING.equals(o.status())).count();
        var ordersById =
                allOrders.stream()
                        .collect(
                                Collectors.toMap(
                                        ExternalOrderingService.OrderData::id,
                                        Function.identity()));
        var allPayments = externalPaymentService.fetchAllPayments();
        var payments =
                allPayments.stream()
                        .filter(
                                p ->
                                        COMPLETED.equals(p.status())
                                                && ordersById.containsKey(p.orderId()))
                        .filter(p -> inPeriod(p.paidAt(), query))
                        .toList();
        double revenue =
                payments.stream().mapToDouble(p -> p.amount() == null ? 0.0 : p.amount()).sum();
        // A commercial order is sold once, even if it has multiple payments. Attribute it to its
        // first
        // completed payment; revenue still includes every completed payment in the selected period.
        var saleDates = new HashMap<Long, LocalDateTime>();
        allPayments.stream()
                .filter(
                        p ->
                                COMPLETED.equals(p.status())
                                        && ordersById.containsKey(p.orderId())
                                        && p.paidAt() != null)
                .forEach(
                        p ->
                                saleDates.merge(
                                        p.orderId(), p.paidAt(), (a, b) -> a.isBefore(b) ? a : b));
        Map<LocalDate, Double> trend = new TreeMap<>();
        double litres = 0;
        for (var entry : saleDates.entrySet()) {
            var order = ordersById.get(entry.getKey());
            if (!CANCELLED.equals(order.status())
                    && inPeriod(entry.getValue(), query)
                    && order.quantityLitres() != null) {
                litres += order.quantityLitres();
                trend.merge(entry.getValue().toLocalDate(), order.quantityLitres(), Double::sum);
            }
        }
        var points =
                trend.entrySet().stream()
                        .map(e -> new SalesTrendPoint(e.getKey(), e.getValue()))
                        .toList();
        return new ProviderAnalytics(
                query.providerId(),
                orders.size(),
                confirmed,
                cancelled,
                revenue,
                monthlyAmounts(
                        payments,
                        ExternalPaymentService.PaymentData::paidAt,
                        ExternalPaymentService.PaymentData::amount),
                pending,
                litres,
                points);
    }

    @Override
    public BuyerAnalytics handle(GetBuyerAnalyticsQuery query) {
        var orders = externalOrderingService.fetchOrdersByCompanyId(query.companyId());
        var payments = externalPaymentService.fetchPaymentsByCompanyId(query.companyId());
        long totalOrders = orders.size();
        double totalSpent =
                payments.stream()
                        .filter(p -> COMPLETED.equals(p.status()))
                        .mapToDouble(p -> p.amount() != null ? p.amount() : 0.0)
                        .sum();
        long completedPayments =
                payments.stream().filter(p -> COMPLETED.equals(p.status())).count();
        long pendingPayments = payments.stream().filter(p -> PENDING.equals(p.status())).count();
        return new BuyerAnalytics(
                query.companyId(),
                totalOrders,
                totalSpent,
                completedPayments,
                pendingPayments,
                monthlyAmounts(
                        payments.stream()
                                .filter(payment -> COMPLETED.equals(payment.status()))
                                .toList(),
                        ExternalPaymentService.PaymentData::paidAt,
                        ExternalPaymentService.PaymentData::amount));
    }

    @Override
    public PlatformSummary handle(GetPlatformSummaryQuery query) {
        var orders = externalOrderingService.fetchAllOrders();
        var deliveries = externalFulfillmentService.fetchAllDeliveryStatuses();
        var payments = externalPaymentService.fetchAllPayments();
        long totalOrders = orders.size();
        long pendingOrders = orders.stream().filter(o -> PENDING.equals(o.status())).count();
        long totalDeliveries = deliveries.size();
        long completedDeliveries = deliveries.stream().filter(DELIVERED::equals).count();
        long totalPayments = payments.size();
        double totalRevenue =
                payments.stream()
                        .filter(p -> COMPLETED.equals(p.status()))
                        .mapToDouble(p -> p.amount() != null ? p.amount() : 0.0)
                        .sum();
        return new PlatformSummary(
                totalOrders,
                totalDeliveries,
                totalPayments,
                totalRevenue,
                pendingOrders,
                completedDeliveries);
    }

    private static boolean inPeriod(LocalDateTime date, GetProviderAnalyticsQuery query) {
        if (query.from() == null && query.to() == null) return true;
        return date != null
                && (query.from() == null || !date.toLocalDate().isBefore(query.from()))
                && (query.to() == null || !date.toLocalDate().isAfter(query.to()));
    }

    private static <T> List<MonthlyAmount> monthlyAmounts(
            List<T> rows, Function<T, LocalDateTime> date, Function<T, Double> amount) {
        Map<YearMonth, Double> grouped = new TreeMap<>();
        rows.stream()
                .filter(row -> date.apply(row) != null)
                .forEach(
                        row -> {
                            var month = YearMonth.from(date.apply(row));
                            grouped.merge(
                                    month,
                                    amount.apply(row) != null ? amount.apply(row) : 0.0,
                                    Double::sum);
                        });
        return grouped.entrySet().stream()
                .map(
                        entry ->
                                new MonthlyAmount(
                                        entry.getKey().toString(),
                                        entry.getKey().getMonthValue(),
                                        entry.getValue()))
                .toList();
    }
}
