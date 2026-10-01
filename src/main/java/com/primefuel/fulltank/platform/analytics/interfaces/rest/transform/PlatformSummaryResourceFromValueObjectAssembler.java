package com.primefuel.fulltank.platform.analytics.interfaces.rest.transform;

import com.primefuel.fulltank.platform.analytics.domain.model.valueobjects.PlatformSummary;
import com.primefuel.fulltank.platform.analytics.interfaces.rest.resources.PlatformSummaryResource;

public final class PlatformSummaryResourceFromValueObjectAssembler {

    private PlatformSummaryResourceFromValueObjectAssembler() {
    }

    public static PlatformSummaryResource toResourceFromValueObject(PlatformSummary summary) {
        return new PlatformSummaryResource(summary.totalOrders(), summary.totalDeliveries(),
                summary.totalPayments(), summary.totalRevenue(),
                summary.pendingOrders(), summary.completedDeliveries());
    }
}
