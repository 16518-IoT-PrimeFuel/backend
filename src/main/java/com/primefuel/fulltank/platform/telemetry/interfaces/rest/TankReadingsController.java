package com.primefuel.fulltank.platform.telemetry.interfaces.rest;

import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import com.primefuel.fulltank.platform.telemetry.application.queryservices.TankReadingsQueryService;
import com.primefuel.fulltank.platform.telemetry.domain.model.queries.GetTankReadingsQuery;
import com.primefuel.fulltank.platform.telemetry.interfaces.rest.transform.ProviderTankReadingResourceFromDomainAssembler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Positive;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping(value = "/api/tanks/{tankId}/readings", produces = "application/json")
@Tag(name = "Telemetría")
public class TankReadingsController {
    private final TankReadingsQueryService queries;
    private final MembershipAccess membershipAccess;

    public TankReadingsController(
            TankReadingsQueryService queries, MembershipAccess membershipAccess) {
        this.queries = queries;
        this.membershipAccess = membershipAccess;
    }

    @GetMapping
    @Operation(
            summary = "Consultar lecturas IoT de un tanque propio",
            description =
                    "Lecturas ACCEPTED del tanque cuando pertenece a la organización activa del"
                        + " usuario. from/to son timestamps ISO UTC inclusivos sobre capturedAt."
                        + " Orden ascendente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lecturas aceptadas o []"),
        @ApiResponse(responseCode = "400", description = "Id, timestamp o período inválido"),
        @ApiResponse(responseCode = "403", description = "Sin organización activa"),
        @ApiResponse(responseCode = "404", description = "Tanque ajeno o inexistente")
    })
    public ResponseEntity<?> get(
            @PathVariable @Positive Long tankId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                queries.handle(new GetTankReadingsQuery(organizationId.get(), tankId, from, to)),
                rows ->
                        rows.stream()
                                .map(ProviderTankReadingResourceFromDomainAssembler::toResource)
                                .toList(),
                HttpStatus.OK);
    }
}
