package com.primefuel.fulltank.platform.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.primefuel.fulltank.platform.analytics.application.internal.outboundservices.acl.ExternalFulfillmentService;
import com.primefuel.fulltank.platform.analytics.application.internal.outboundservices.acl.ExternalOrderingService;
import com.primefuel.fulltank.platform.analytics.application.internal.outboundservices.acl.ExternalPaymentService;
import com.primefuel.fulltank.platform.analytics.application.internal.outboundservices.acl.ExternalPaymentService.PaymentData;
import com.primefuel.fulltank.platform.analytics.application.internal.queryservices.AnalyticsQueryServiceImpl;
import com.primefuel.fulltank.platform.analytics.domain.model.queries.GetBuyerAnalyticsQuery;
import com.primefuel.fulltank.platform.analytics.domain.model.valueobjects.DailyAmount;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

class BuyerSpendingTrendTest {

    @Test
    void spendingTrendSumsCompletedPaymentsPerDayInChronologicalOrder() {
        var ordering = mock(ExternalOrderingService.class);
        var payments = mock(ExternalPaymentService.class);
        when(ordering.fetchOrdersByCompanyId(7L)).thenReturn(List.of());
        when(payments.fetchPaymentsByCompanyId(7L))
                .thenReturn(
                        List.of(
                                new PaymentData(1L, "COMPLETED", 30.0, LocalDateTime.of(2026, 10, 5, 9, 0)),
                                new PaymentData(2L, "COMPLETED", 10.0, LocalDateTime.of(2026, 10, 2, 9, 0)),
                                new PaymentData(3L, "COMPLETED", 5.0, LocalDateTime.of(2026, 10, 5, 18, 0)),
                                new PaymentData(4L, "PENDING", 99.0, LocalDateTime.of(2026, 10, 5, 9, 0)),
                                new PaymentData(5L, "COMPLETED", 99.0, null)));
        var service =
                new AnalyticsQueryServiceImpl(
                        ordering, payments, mock(ExternalFulfillmentService.class));

        var analytics = service.handle(new GetBuyerAnalyticsQuery(7L));

        assertThat(analytics.spendingTrend())
                .containsExactly(
                        new DailyAmount(LocalDate.of(2026, 10, 2), 10.0),
                        new DailyAmount(LocalDate.of(2026, 10, 5), 35.0));
    }
}
