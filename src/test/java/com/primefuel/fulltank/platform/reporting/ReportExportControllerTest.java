package com.primefuel.fulltank.platform.reporting;

import com.primefuel.fulltank.platform.reporting.application.queryservices.AnalyticsQueryService;
import com.primefuel.fulltank.platform.reporting.domain.model.valueobjects.BuyerAnalytics;
import com.primefuel.fulltank.platform.reporting.domain.model.valueobjects.MonthlyAmount;
import com.primefuel.fulltank.platform.reporting.interfaces.rest.ReportExportController;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportExportControllerTest {
    @Test
    void exportsBuyerAnalyticsAsDownloadableCsv() {
        var analytics = mock(AnalyticsQueryService.class);
        when(analytics.handle(new com.primefuel.fulltank.platform.reporting.domain.model.queries.GetBuyerAnalyticsQuery(3L)))
                .thenReturn(new BuyerAnalytics(3L, 4, 1200.0, 2, 1,
                        List.of(new MonthlyAmount("2026-09", 9, 1200.0))));

        var response = new ReportExportController(analytics).buyer(3L);

        assertEquals("text/csv", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("buyer-3-report.csv"));
        assertTrue(new String(response.getBody()).contains("totalSpent,1200.0"));
    }
}
