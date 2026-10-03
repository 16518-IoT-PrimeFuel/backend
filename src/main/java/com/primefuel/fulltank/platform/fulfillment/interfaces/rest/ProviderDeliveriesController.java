package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.queryservices.DeliveryQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveriesByProviderQuery;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.ProviderDeliveryResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.transform.ProviderDeliveryResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Positive;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(value = "/api/deliveries", produces = "application/json")
@Tag(name = "Ciclo de entrega")
public class ProviderDeliveriesController {
    private final DeliveryQueryService queries;
    private final TenantAccess access;
    private final ProviderDeliveryResourceFromDomainAssembler assembler;

    public ProviderDeliveriesController(
            DeliveryQueryService queries,
            TenantAccess access,
            ProviderDeliveryResourceFromDomainAssembler assembler) {
        this.queries = queries;
        this.access = access;
        this.assembler = assembler;
    }

    @GetMapping
    @PreAuthorize("@currentUserAccess.isProvider()")
    @Operation(
            summary = "Listar entregas del distribuidor",
            description =
                    "Tenant autenticado. providerId opcional debe coincidir. date ISO filtra fecha"
                            + " programada. Campos asociados pueden ser null en datos heredados.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Entregas propias o lista vacía"),
        @ApiResponse(responseCode = "400", description = "Identificador o fecha inválida"),
        @ApiResponse(responseCode = "403", description = "Rol incorrecto o proveedor ajeno")
    })
    public ResponseEntity<List<ProviderDeliveryResource>> list(
            @RequestParam(required = false) @Positive Long providerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        var current = access.currentProviderId();
        if (current.isEmpty() || providerId != null && !current.get().equals(providerId))
            throw new AccessDeniedException("Provider identity or ownership required");
        return ResponseEntity.ok(
                queries.handle(new GetDeliveriesByProviderQuery(current.get(), date)).stream()
                        .map(assembler::toResourceFromDomain)
                        .toList());
    }
}
