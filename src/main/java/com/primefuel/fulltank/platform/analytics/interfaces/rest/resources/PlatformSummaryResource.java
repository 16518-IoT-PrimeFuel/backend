package com.primefuel.fulltank.platform.analytics.interfaces.rest.resources;

public record PlatformSummaryResource(long totalOrders, long totalDeliveries, long totalPayments,
                                      double totalRevenue, long pendingOrders, long completedDeliveries) {
}
