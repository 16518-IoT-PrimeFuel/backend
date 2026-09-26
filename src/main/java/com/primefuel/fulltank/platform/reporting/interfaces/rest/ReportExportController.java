package com.primefuel.fulltank.platform.reporting.interfaces.rest;

import com.primefuel.fulltank.platform.reporting.application.queryservices.AnalyticsQueryService;
import com.primefuel.fulltank.platform.reporting.domain.model.queries.GetBuyerAnalyticsQuery;
import com.primefuel.fulltank.platform.reporting.domain.model.queries.GetPlatformSummaryQuery;
import com.primefuel.fulltank.platform.reporting.domain.model.queries.GetProviderAnalyticsQuery;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v2/reports")
public class ReportExportController {
    private final AnalyticsQueryService analytics;

    public ReportExportController(AnalyticsQueryService analytics) {
        this.analytics = analytics;
    }

    @GetMapping(value = "/platform/export", produces = "text/csv")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> platform() {
        var summary = analytics.handle(new GetPlatformSummaryQuery());
        return download("platform-report.csv", "metric,value\n"
                + "totalOrders," + summary.totalOrders() + "\n"
                + "totalDeliveries," + summary.totalDeliveries() + "\n"
                + "totalPayments," + summary.totalPayments() + "\n"
                + "totalRevenue," + summary.totalRevenue() + "\n"
                + "pendingOrders," + summary.pendingOrders() + "\n"
                + "completedDeliveries," + summary.completedDeliveries() + "\n");
    }

    @GetMapping(value = "/buyers/{companyId}/export", produces = "text/csv")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<byte[]> buyer(@PathVariable Long companyId) {
        var report = analytics.handle(new GetBuyerAnalyticsQuery(companyId));
        return download("buyer-" + companyId + "-report.csv", "metric,value\n"
                + "companyId," + report.companyId() + "\n"
                + "totalOrders," + report.totalOrders() + "\n"
                + "totalSpent," + report.totalSpent() + "\n"
                + "completedPayments," + report.completedPayments() + "\n"
                + "pendingPayments," + report.pendingPayments() + "\n"
                + report.monthlySpending().stream().map(month -> "monthlySpending." + month.month() + "," + month.amount() + "\n").reduce("", String::concat));
    }

    @GetMapping(value = "/providers/{providerId}/export", produces = "text/csv")
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public ResponseEntity<byte[]> provider(@PathVariable Long providerId) {
        var report = analytics.handle(new GetProviderAnalyticsQuery(providerId));
        return download("provider-" + providerId + "-report.csv", "metric,value\n"
                + "providerId," + report.providerId() + "\n"
                + "totalOrders," + report.totalOrders() + "\n"
                + "confirmedOrders," + report.confirmedOrders() + "\n"
                + "cancelledOrders," + report.cancelledOrders() + "\n"
                + "totalRevenue," + report.totalRevenue() + "\n"
                + report.monthlyRevenue().stream().map(month -> "monthlyRevenue." + month.month() + "," + month.amount() + "\n").reduce("", String::concat));
    }

    private ResponseEntity<byte[]> download(String filename, String content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
