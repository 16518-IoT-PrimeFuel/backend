package com.primefuel.fulltank.platform.analytics.domain.model.valueobjects;

public record PlatformSummary(long totalOrders, long totalDeliveries, long totalPayments,
                              double totalRevenue, long pendingOrders, long completedDeliveries) {
}
