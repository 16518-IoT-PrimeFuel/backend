package com.primefuel.fulltank.platform.safety.valve.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.safety.valve.application.ValveObservationService;
import com.primefuel.fulltank.platform.safety.valve.interfaces.rest.resources.ValveObservationResource;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ErrorResponseAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Recibe observaciones lógicas de la aplicación del conductor; no se comunica con hardware físico. */
@RestController
@RequestMapping("/api/deliveries")
@Tag(name = "Observaciones de válvula", description = "Confirmaciones lógicas y eventos de seguridad informados por el conductor")
public class ValveObservationsController {
    private final ValveObservationService observations;
    private final DeliveryTrackingLookup deliveries;
    private final FleetCatalog fleet;
    private final MembershipAccess membership;

    public ValveObservationsController(ValveObservationService observations, DeliveryTrackingLookup deliveries,
            FleetCatalog fleet, MembershipAccess membership) {
        this.observations = observations; this.deliveries = deliveries; this.fleet = fleet; this.membership = membership;
    }

    /** Registra el estado informado por el conductor asignado y concilia una orden OPEN pendiente. */
    @Operation(summary = "Informar observación de válvula", description = "El conductor asignado informa OPEN o CLOSED; una confirmación OPEN no autorizada se registra como incidente de seguridad.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Observación registrada o confirmación repetida sin duplicar efectos."),
            @ApiResponse(responseCode = "202", description = "La confirmación OPEN no coincide con una orden y se registró como incidente de seguridad."),
            @ApiResponse(responseCode = "400", description = "El cuerpo de la observación no es válido."),
            @ApiResponse(responseCode = "403", description = "El usuario no es el conductor asignado o la asignación cruza tenants."),
            @ApiResponse(responseCode = "404", description = "La entrega o el conductor asignado no existe.")
    })
    @PostMapping("/{deliveryId}/valve-observations")
    public ResponseEntity<?> observe(@PathVariable Long deliveryId, @Valid @RequestBody ValveObservationResource body) {
        var userId = membership.currentUserId();
        if (userId.isEmpty()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        var assigned = deliveries.findAssignedDelivery(deliveryId);
        if (assigned.isEmpty()) return ResponseEntity.notFound().build();
        var driver = fleet.findDriver(assigned.get().driverId());
        if (driver.isEmpty()) return ResponseEntity.notFound().build();
        if (!assigned.get().providerId().equals(driver.get().providerId()) || driver.get().userId() == null
                || !driver.get().userId().equals(userId.get())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            boolean incident = observations.observe(deliveryId, assigned.get().providerId(), body.state(), body.observedAt(), body.commandId());
            return ResponseEntity.status(incident ? HttpStatus.ACCEPTED : HttpStatus.OK).build();
        } catch (IllegalArgumentException e) {
            return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                    ApplicationError.validationError("state", e.getMessage()));
        }
    }
}
