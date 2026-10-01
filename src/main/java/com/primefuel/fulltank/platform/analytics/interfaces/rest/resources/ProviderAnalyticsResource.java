package com.primefuel.fulltank.platform.analytics.interfaces.rest.resources;

import com.primefuel.fulltank.platform.analytics.domain.model.valueobjects.MonthlyAmount;
import java.util.List;

public record ProviderAnalyticsResource(Long providerId, long totalOrders, long confirmedOrders,
                                        long cancelledOrders, double totalRevenue,
                                        List<MonthlyAmount> monthlyRevenue) {
}
