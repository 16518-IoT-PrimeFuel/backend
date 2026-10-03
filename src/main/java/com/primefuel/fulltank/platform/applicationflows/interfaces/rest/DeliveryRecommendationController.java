package com.primefuel.fulltank.platform.applicationflows.interfaces.rest;

import com.primefuel.fulltank.platform.applicationflows.*;
import com.primefuel.fulltank.platform.applicationflows.interfaces.rest.resources.DeliveryRecommendationResource;
import com.primefuel.fulltank.platform.applicationflows.interfaces.rest.transform.DeliveryRecommendationResourceFromResultAssembler;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.*;

import org.springframework.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping(value = "/api/deliveries/recommendation", produces = "application/json")
@Tag(name = "Asignación de entregas")
public class DeliveryRecommendationController {
    private final DeliveryRecommendationQueryService queries;
    private final TenantAccess access;

    public DeliveryRecommendationController(
            DeliveryRecommendationQueryService queries, TenantAccess access) {
        this.queries = queries;
        this.access = access;
    }

    @GetMapping
    @PreAuthorize("@currentUserAccess.isProvider()")
    @Operation(
            summary = "Recomendar conductor y cisterna",
            description =
                    "Solo orden propia respaldada por solicitud aceptada sin asignar. Reutiliza"
                        + " elegibilidad y descarta reservas activas superpuestas. Cisterna de"
                        + " menor capacidad suficiente en litros; empates por id; conductor de"
                        + " menor id. windowStart/windowEnd ISO UTC opcionales juntos; sin ellos se"
                        + " usa todo el día programado UTC. No reserva ni asigna. El modelo no"
                        + " permite optimizar ruta ni compatibilidad específica de"
                        + " cisterna/producto.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Propuesta o recommended=false con motivo",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        DeliveryRecommendationResource.class))),
        @ApiResponse(responseCode = "400", description = "Ventana o id inválido"),
        @ApiResponse(responseCode = "403", description = "Sin proveedor autenticado"),
        @ApiResponse(
                responseCode = "404",
                description = "Orden ajena o sin solicitud correlacionada"),
        @ApiResponse(responseCode = "409", description = "Solicitud no aceptada o ya asignada")
    })
    public ResponseEntity<?> get(
            @RequestParam @Positive Long orderId,
            @RequestParam(required = false) Instant windowStart,
            @RequestParam(required = false) Instant windowEnd) {
        var provider = access.currentProviderId();
        if (provider.isEmpty())
            throw new AccessDeniedException("Provider identity or ownership required");
        return ResponseEntityAssembler.toResponseEntityFromResult(
                queries.handle(
                        new DeliveryRecommendationQuery(
                                provider.get(), orderId, windowStart, windowEnd)),
                DeliveryRecommendationResourceFromResultAssembler::toResource,
                HttpStatus.OK);
    }
}
