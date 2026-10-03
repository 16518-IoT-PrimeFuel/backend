package com.primefuel.fulltank.platform.telemetry.interfaces.rest;

import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import com.primefuel.fulltank.platform.telemetry.application.queryservices.ProviderTankReadingsQueryService;
import com.primefuel.fulltank.platform.telemetry.domain.model.queries.GetProviderTankReadingsQuery;
import com.primefuel.fulltank.platform.telemetry.interfaces.rest.resources.ProviderTankReadingResource;
import com.primefuel.fulltank.platform.telemetry.interfaces.rest.transform.ProviderTankReadingResourceFromDomainAssembler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Positive;

import org.springframework.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping(value = "/api/provider/tanks/{tankId}/readings", produces = "application/json")
@Tag(name = "Telemetría")
public class ProviderTankReadingsController {
    private final ProviderTankReadingsQueryService queries;
    private final TenantAccess access;

    public ProviderTankReadingsController(
            ProviderTankReadingsQueryService queries, TenantAccess access) {
        this.queries = queries;
        this.access = access;
    }

    @GetMapping
    @PreAuthorize("@currentUserAccess.isProvider()")
    @Operation(
            summary = "Consultar lecturas IoT de un tanque vinculado",
            description =
                    "Solo lecturas ACCEPTED atribuidas al tanque y comprador al capturarse. from/to"
                        + " son timestamps ISO UTC inclusivos, filtran capturedAt. Orden"
                        + " ascendente. Conserva historia de dispositivos reemplazados y excluye"
                        + " cuarentena. capturedAt/receivedAt permiten mostrar antigüedad.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Lecturas aceptadas o []",
                content =
                        @Content(
                                array =
                                        @ArraySchema(
                                                schema =
                                                        @Schema(
                                                                implementation =
                                                                        ProviderTankReadingResource
                                                                                .class)))),
        @ApiResponse(responseCode = "400", description = "Id, timestamp o período inválido"),
        @ApiResponse(responseCode = "403", description = "Sin proveedor autenticado"),
        @ApiResponse(responseCode = "404", description = "Tanque ajeno o inexistente")
    })
    public ResponseEntity<?> get(
            @PathVariable @Positive Long tankId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        var provider = access.currentProviderId();
        if (provider.isEmpty())
            throw new AccessDeniedException("Provider identity or ownership required");
        return ResponseEntityAssembler.toResponseEntityFromResult(
                queries.handle(new GetProviderTankReadingsQuery(provider.get(), tankId, from, to)),
                rows ->
                        rows.stream()
                                .map(ProviderTankReadingResourceFromDomainAssembler::toResource)
                                .toList(),
                HttpStatus.OK);
    }
}
