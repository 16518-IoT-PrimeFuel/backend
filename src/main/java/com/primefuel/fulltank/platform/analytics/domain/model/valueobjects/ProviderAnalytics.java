package com.primefuel.fulltank.platform.analytics.domain.model.valueobjects;

import java.util.List;

public record ProviderAnalytics(
        Long providerId,
        long totalOrders,
        long confirmedOrders,
        long cancelledOrders,
        double totalRevenue,
        List<MonthlyAmount> monthlyRevenue,
        long pendingOrders,
        double totalFuelSoldLitres,
        List<SalesTrendPoint> salesTrend) {}
