package com.primefuel.fulltank.platform.analytics.interfaces.rest;

import com.primefuel.fulltank.platform.analytics.application.queryservices.AnalyticsQueryService;
import com.primefuel.fulltank.platform.analytics.domain.model.queries.GetBuyerAnalyticsQuery;
import com.primefuel.fulltank.platform.analytics.domain.model.queries.GetPlatformSummaryQuery;
import com.primefuel.fulltank.platform.analytics.domain.model.queries.GetProviderAnalyticsQuery;
import com.primefuel.fulltank.platform.analytics.interfaces.rest.resources.BuyerAnalyticsResource;
import com.primefuel.fulltank.platform.analytics.interfaces.rest.resources.PlatformSummaryResource;
import com.primefuel.fulltank.platform.analytics.interfaces.rest.resources.ProviderAnalyticsResource;
import com.primefuel.fulltank.platform.analytics.interfaces.rest.transform.BuyerAnalyticsResourceFromValueObjectAssembler;
import com.primefuel.fulltank.platform.analytics.interfaces.rest.transform.PlatformSummaryResourceFromValueObjectAssembler;
import com.primefuel.fulltank.platform.analytics.interfaces.rest.transform.ProviderAnalyticsResourceFromValueObjectAssembler;
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
@RequestMapping(value = "/api/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analítica", description = "Indicadores agregados para administración, distribuidores y empresas compradoras")
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    public AnalyticsController(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    /**
     * Consulta el resumen agregado de la plataforma.
     *
     * <p>Disponible únicamente para usuarios con autoridad ROLE_ADMIN.</p>
     */
    @Operation(summary = "Consultar resumen de plataforma",
            description = "Devuelve métricas agregadas de toda la plataforma; requiere autoridad ROLE_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen de plataforma devuelto."),
            @ApiResponse(responseCode = "403", description = "El usuario no cuenta con autoridad ROLE_ADMIN.")
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
     * Consulta los indicadores de un distribuidor.
     *
     * <p>Solo el distribuidor propietario puede consultarlos; los resultados abarcan su tenant completo.</p>
     */
    @Operation(summary = "Consultar indicadores del distribuidor",
            description = "Devuelve métricas agregadas del tenant distribuidor cuando el usuario pertenece a esa empresa; Requiere cuenta con ROLE_PROVIDER")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Indicadores del distribuidor devueltos."),
            @ApiResponse(responseCode = "403", description = "El usuario no pertenece al distribuidor solicitado.")
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
     * Consulta los indicadores de una empresa compradora.
     *
     * <p>Solo el tenant de la empresa compradora propietaria puede consultarlos.</p>
     */
    @Operation(summary = "Consultar indicadores de empresa compradora",
            description = "Devuelve métricas agregadas de la empresa indicada cuando el usuario pertenece a ella.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Indicadores de la empresa compradora devueltos."),
            @ApiResponse(responseCode = "403", description = "El usuario no pertenece a la empresa compradora solicitada.")
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
