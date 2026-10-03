package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.internal.queryservices.DeliveryValveObservationQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryValveObservationsQuery;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.DeliveryValveObservationResource;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;

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

@RestController
@RequestMapping(
        value = "/api/deliveries/{deliveryId}/valve-observations",
        produces = "application/json")
@Tag(name = "Observaciones de válvula")
public class DeliveryValveObservationsController {
    private final DeliveryValveObservationQueryService queries;
    private final TenantAccess access;

    public DeliveryValveObservationsController(
            DeliveryValveObservationQueryService queries, TenantAccess access) {
        this.queries = queries;
        this.access = access;
    }

    @GetMapping
    @PreAuthorize("@currentUserAccess.isProvider()")
    @Operation(
            summary = "Consultar observaciones de válvula de una entrega propia",
            description =
                    "Historia ascendente del diario inmutable; último elemento es el último estado"
                        + " lógico registrado. recordedAt es el instante de registro del evento, no"
                        + " captura de hardware. Incluye apertura espontánea como OPEN con"
                        + " unauthorized=true. No envía comandos ni cambia la autorización del"
                        + " conductor para POST.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Historia lógica o []",
                content =
                        @Content(
                                array =
                                        @ArraySchema(
                                                schema =
                                                        @Schema(
                                                                implementation =
                                                                        DeliveryValveObservationResource
                                                                                .class)))),
        @ApiResponse(responseCode = "400", description = "Id inválido"),
        @ApiResponse(responseCode = "403", description = "Sin proveedor autenticado"),
        @ApiResponse(responseCode = "404", description = "Entrega ajena o inexistente")
    })
    public ResponseEntity<?> get(@PathVariable @Positive Long deliveryId) {
        var provider = access.currentProviderId();
        if (provider.isEmpty())
            throw new AccessDeniedException("Provider identity or ownership required");
        return ResponseEntityAssembler.toResponseEntityFromResult(
                queries.handle(new GetDeliveryValveObservationsQuery(deliveryId, provider.get())),
                rows ->
                        rows.stream()
                                .map(
                                        e ->
                                                new DeliveryValveObservationResource(
                                                        e.id(),
                                                        e.state(),
                                                        e.unauthorized(),
                                                        e.commandId(),
                                                        e.recordedAt()))
                                .toList(),
                HttpStatus.OK);
    }
}
