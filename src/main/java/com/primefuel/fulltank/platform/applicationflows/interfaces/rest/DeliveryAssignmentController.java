package com.primefuel.fulltank.platform.applicationflows.interfaces.rest;

import com.primefuel.fulltank.platform.applicationflows.AssignDeliveryFlow;
import com.primefuel.fulltank.platform.applicationflows.AssignDeliveryFlowCommand;
import com.primefuel.fulltank.platform.applicationflows.AssignDeliveryResult;
import com.primefuel.fulltank.platform.applicationflows.interfaces.rest.resources.AssignDeliveryResource;
import com.primefuel.fulltank.platform.applicationflows.interfaces.rest.resources.AssignmentResource;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asigna una entrega mediante {@code POST /api/deliveries}. Delega en {@link AssignDeliveryFlow},
 * que consume la aceptación y reserva suministro y flota en una transacción.
 *
 * <p>El distribuidor se obtiene del principal autenticado, nunca del cuerpo. La orden debe estar respaldada
 * por una solicitud de reposición aceptada por ese distribuidor. Repetir el mismo {@code commandId} devuelve
 * la misma entrega.
 *
 * <p>Este adaptador HTTP forma parte de la raíz de composición para mantener los módulos de dominio desacoplados.</p>
 */
@RestController
@RequestMapping(value = "/api/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Asignación de entregas", description = "Asignación transaccional de solicitudes aceptadas")
public class DeliveryAssignmentController {

    private final AssignDeliveryFlow assignDeliveryFlow;
    private final TenantAccess tenantAccess;

    public DeliveryAssignmentController(AssignDeliveryFlow assignDeliveryFlow, TenantAccess tenantAccess) {
        this.assignDeliveryFlow = assignDeliveryFlow;
        this.tenantAccess = tenantAccess;
    }

    /** Consume la aceptación de reposición y asigna una entrega al tenant distribuidor autenticado. */
    @Operation(summary = "Asignar entrega para una orden aceptada",
            description = "Consume la aceptación de reposición de la orden, reserva suministro y flota, y crea la entrega en una transacción; es idempotente por commandId.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entrega asignada o entrega existente devuelta para el commandId."),
            @ApiResponse(responseCode = "400", description = "Falta commandId u orderId, o la ventana o el volumen no son válidos."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado como tenant distribuidor."),
            @ApiResponse(responseCode = "404", description = "La orden no está respaldada por una solicitud aceptada por el distribuidor autenticado."),
            @ApiResponse(responseCode = "409", description = "La solicitud no está aceptada, la aceptación ya se consumió o no hay disponibilidad de suministro o flota.")
    })
    @PostMapping
    public ResponseEntity<?> assign(@RequestBody AssignDeliveryResource resource) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var command = new AssignDeliveryFlowCommand(resource.commandId(), resource.orderId(), providerId.get(),
                resource.driverId(), resource.tankerId(), resource.windowStart(), resource.windowEnd(),
                resource.scheduledDate(), resource.notes());
        var result = assignDeliveryFlow.assign(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, DeliveryAssignmentController::toResource, HttpStatus.CREATED);
    }

    private static AssignmentResource toResource(AssignDeliveryResult result) {
        return new AssignmentResource(result.deliveryId(), result.orderId(), result.providerId(),
                result.driverId(), result.tankerId(), result.supplyReservationId(), result.fleetReservationId(),
                result.physicalState(), result.commandId());
    }
}
