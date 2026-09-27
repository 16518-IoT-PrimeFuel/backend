package com.primefuel.fulltank.platform.ordering.interfaces.rest;

import com.primefuel.fulltank.platform.ordering.application.internal.commandservices.FuelRequestService;
import com.primefuel.fulltank.platform.ordering.application.internal.commandservices.LegacyFuelRequestBridge;
import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.entities.FuelRequestPersistenceEntity;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.*;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.transform.FuelOrderResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services.CurrentUserAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fuel-requests")
@Tag(name = "Solicitudes de combustible heredadas", description = "Compatibilidad temporal para solicitudes v1 vinculadas al flujo de reposición")
public class FuelRequestsController {
    private final FuelRequestService service;
    private final CurrentUserAccess currentUserAccess;

    /** Field-injected so the existing (frozen) controller constructor is left untouched. */
    @Autowired
    private LegacyFuelRequestBridge bridge;

    public FuelRequestsController(FuelRequestService service, CurrentUserAccess currentUserAccess) {
        this.service = service;
        this.currentUserAccess = currentUserAccess;
    }

    /**
     * Crea una solicitud de combustible para la empresa compradora del usuario autenticado.
     *
     * <p>La operación crea también una solicitud de reposición vinculada, que conserva el ciclo de revisión
     * en el módulo correspondiente y mantiene el identificador heredado. El producto debe pertenecer al distribuidor indicado.</p>
     */
    @Operation(summary = "Crear solicitud de combustible heredada",
            description = "Crea una solicitud v1 y su solicitud de reposición vinculada para una empresa compradora del usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitud de combustible creada."),
            @ApiResponse(responseCode = "400", description = "El producto de combustible no existe o no pertenece al distribuidor indicado."),
            @ApiResponse(responseCode = "403", description = "La empresa compradora del cuerpo no pertenece al usuario autenticado.")
    })
    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsCompany(#resource.buyerCompanyId())")
    public ResponseEntity<FuelRequestResource> create(@RequestBody CreateFuelRequestResource resource) {
        return new ResponseEntity<>(toResource(bridge.create(resource)), HttpStatus.CREATED);
    }

    /**
     * Lista solicitudes filtradas por empresa compradora o distribuidor.
     *
     * <p>Se requiere exactamente uno de los filtros y debe pertenecer al usuario; se rechaza indicar ambos o ninguno.</p>
     */
    @Operation(summary = "Listar solicitudes de combustible",
            description = "Devuelve solicitudes de una sola dimensión propia: la empresa compradora o el tenant distribuidor del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de solicitudes."),
            @ApiResponse(responseCode = "400", description = "Se indicaron a la vez buyerCompanyId y providerId."),
            @ApiResponse(responseCode = "403", description = "No se indicó filtro o el filtro indicado no pertenece al usuario.")
    })
    @GetMapping
    public ResponseEntity<List<FuelRequestResource>> findAll(@RequestParam(required = false) Long buyerCompanyId,
                                                               @RequestParam(required = false) Long providerId) {
        if (buyerCompanyId != null && providerId != null) {
            return ResponseEntity.badRequest().build();
        }
        if (buyerCompanyId != null && !currentUserAccess.ownsCompany(buyerCompanyId)
                || providerId != null && !currentUserAccess.ownsProvider(providerId)
                || buyerCompanyId == null && providerId == null) {
            return ResponseEntity.status(403).build();
        }
        var requests = service.findAll(buyerCompanyId, providerId).stream()
                .map(FuelRequestsController::toResource).toList();
        return ResponseEntity.ok(requests);
    }

    /**
     * Consulta una solicitud de combustible por identificador.
     *
     * <p>Puede consultarla la empresa compradora o el distribuidor de la solicitud; los demás usuarios reciben una respuesta de no encontrada.</p>
     */
    @Operation(summary = "Consultar solicitud de combustible por identificador",
            description = "Devuelve la solicitud indicada cuando el usuario pertenece a la empresa compradora o al distribuidor correspondiente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud de combustible devuelta."),
            @ApiResponse(responseCode = "404", description = "La solicitud no existe o no pertenece al usuario autenticado.")
    })
    @GetMapping("/{requestId}")
    public ResponseEntity<FuelRequestResource> findById(@PathVariable Long requestId) {
        return service.findById(requestId)
                .filter(request -> currentUserAccess.ownsCompanyOrProvider(
                        request.getBuyerCompanyId(), request.getProviderId()))
                .map(request -> ResponseEntity.ok(toResource(request)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Acepta una solicitud de combustible pendiente.
     *
     * <p>Solo puede aceptarla el distribuidor destinatario. La aceptación consume una vez la revisión vinculada
     * y crea la orden de combustible; una aceptación repetida falla.</p>
     */
    @Operation(summary = "Aceptar solicitud de combustible",
            description = "Acepta una solicitud pendiente en nombre del distribuidor destinatario y crea la orden de combustible resultante.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud aceptada y orden de combustible creada."),
            @ApiResponse(responseCode = "400", description = "No se encontró la solicitud o su producto al crear la orden."),
            @ApiResponse(responseCode = "404", description = "La solicitud no existe o no está dirigida al tenant distribuidor autenticado."),
            @ApiResponse(responseCode = "500", description = "La solicitud ya fue aceptada o no se pudo aceptar la revisión vinculada.")
    })
    @PostMapping("/{requestId}/accept")
    public ResponseEntity<?> accept(@PathVariable Long requestId) {
        if (!ownsRequestAsProvider(requestId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(FuelOrderResourceFromEntityAssembler.toResourceFromEntity(bridge.accept(requestId)));
    }

    /**
     * Rechaza una solicitud de combustible pendiente.
     *
     * <p>Solo puede rechazarla el distribuidor destinatario. Se requiere un motivo, que se propaga a la revisión de reposición vinculada.</p>
     */
    @Operation(summary = "Rechazar solicitud de combustible",
            description = "Rechaza una solicitud pendiente para el distribuidor destinatario y registra el motivo indicado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud de combustible rechazada."),
            @ApiResponse(responseCode = "400", description = "Falta el motivo de rechazo o no se encontró la solicitud."),
            @ApiResponse(responseCode = "404", description = "La solicitud no existe o no está dirigida al tenant distribuidor autenticado."),
            @ApiResponse(responseCode = "500", description = "La solicitud no está pendiente y no puede rechazarse.")
    })
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<FuelRequestResource> reject(@PathVariable Long requestId,
                                                      @RequestBody RejectFuelRequestResource resource) {
        if (!ownsRequestAsProvider(requestId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toResource(bridge.reject(requestId, resource.reason())));
    }

    private boolean ownsRequestAsProvider(Long requestId) {
        return service.findById(requestId)
                .filter(request -> currentUserAccess.ownsProvider(request.getProviderId()))
                .isPresent();
    }

    private static FuelRequestResource toResource(FuelRequestPersistenceEntity r) {
        return new FuelRequestResource(r.getId(), r.getBuyerCompanyId(), r.getProviderId(), r.getEquipmentId(),
                r.getFuelProductId(), r.getFuelType(), r.getProductName(), r.getQuantity(), r.getUnit(),
                r.getUnitPrice(), r.getDeliveryAddress(), r.getDeliveryDate(), r.getStatus(), r.getSource(),
                r.getRejectionReason(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
