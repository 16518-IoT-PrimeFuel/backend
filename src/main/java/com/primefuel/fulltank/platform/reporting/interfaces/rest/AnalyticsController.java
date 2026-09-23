package com.primefuel.fulltank.platform.reporting.interfaces.rest;

import com.primefuel.fulltank.platform.reporting.application.queryservices.AnalyticsQueryService;
import com.primefuel.fulltank.platform.reporting.domain.model.queries.GetBuyerAnalyticsQuery;
import com.primefuel.fulltank.platform.reporting.domain.model.queries.GetPlatformSummaryQuery;
import com.primefuel.fulltank.platform.reporting.domain.model.queries.GetProviderAnalyticsQuery;
import com.primefuel.fulltank.platform.reporting.interfaces.rest.resources.BuyerAnalyticsResource;
import com.primefuel.fulltank.platform.reporting.interfaces.rest.resources.PlatformSummaryResource;
import com.primefuel.fulltank.platform.reporting.interfaces.rest.resources.ProviderAnalyticsResource;
import com.primefuel.fulltank.platform.reporting.interfaces.rest.transform.BuyerAnalyticsResourceFromValueObjectAssembler;
import com.primefuel.fulltank.platform.reporting.interfaces.rest.transform.PlatformSummaryResourceFromValueObjectAssembler;
import com.primefuel.fulltank.platform.reporting.interfaces.rest.transform.ProviderAnalyticsResourceFromValueObjectAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping(value = "/api/v1/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analytics", description = "Analytics and reporting endpoints")
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    public AnalyticsController(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    /**
     * Returns the platform-wide summary.
     *
     * <p>Administrative endpoint; restricted to callers holding the ROLE_ADMIN authority.</p>
     */
    @Operation(summary = "Get the platform summary",
            description = "Returns aggregated platform-wide metrics. Restricted to administrators.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Platform summary returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not hold the ROLE_ADMIN authority.")
    })
    @GetMapping("/platform")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PlatformSummaryResource> getPlatformSummary() {
        var summary = analyticsQueryService.handle(new GetPlatformSummaryQuery());
        return new ResponseEntity<>(
                PlatformSummaryResourceFromValueObjectAssembler.toResourceFromValueObject(summary),
                HttpStatus.OK);
    }

    /**
     * Returns the analytics of a provider company.
     *
     * <p>Only the provider that owns the requested company may read its analytics; results are
     * computed for the whole provider tenant.</p>
     */
    @Operation(summary = "Get provider analytics",
            description = "Returns the aggregated metrics of the given provider company when the caller owns it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider analytics returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not the owner of this provider company.")
    })
    @GetMapping("/providers/{providerId}")
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public ResponseEntity<ProviderAnalyticsResource> getProviderAnalytics(@PathVariable Long providerId) {
        var analytics = analyticsQueryService.handle(new GetProviderAnalyticsQuery(providerId));
        return new ResponseEntity<>(
                ProviderAnalyticsResourceFromValueObjectAssembler.toResourceFromValueObject(analytics),
                HttpStatus.OK);
    }

    /**
     * Returns the analytics of a buyer company.
     *
     * <p>Only the buyer that owns the requested company may read its analytics.</p>
     */
    @Operation(summary = "Get buyer analytics",
            description = "Returns the aggregated metrics of the given buyer company when the caller owns it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Buyer analytics returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not the owner of this buyer company.")
    })
    @GetMapping("/buyers/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<BuyerAnalyticsResource> getBuyerAnalytics(@PathVariable Long companyId) {
        var analytics = analyticsQueryService.handle(new GetBuyerAnalyticsQuery(companyId));
        return new ResponseEntity<>(
                BuyerAnalyticsResourceFromValueObjectAssembler.toResourceFromValueObject(analytics),
                HttpStatus.OK);
    }
}
