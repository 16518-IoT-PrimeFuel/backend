package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices.DeliveryLifecycleServiceImpl;
import com.primefuel.fulltank.platform.fulfillment.application.queryservices.DeliveryQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.ArriveDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.AssignDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CancelDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CompletePhysicalDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.FailDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.StartDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByIdQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryStateTransitionRepository;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.CompleteDeliveryResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.DeliveryTransitionResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.DeliveryResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.FailDeliveryResource;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * Ciclo físico de entrega (S14/T14-A): asignar, iniciar, llegar, completar, fallar y cancelar.
 * Solo el distribuidor propietario puede avanzar la entrega. Las transiciones inválidas responden 409,
 * el volumen de evidencia inválido responde 400 y un volumen de orden no resoluble al completar responde 422.
 */
@RestController
@RequestMapping(value = "/api/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Ciclo de entrega", description = "Transiciones del estado físico de una entrega")
public class DeliveriesController {

    private final DeliveryLifecycleServiceImpl deliveryLifecycleService;
    private final DeliveryQueryService deliveryQueryService;
    private final DeliveryStateTransitionRepository transitionRepository;
    private final TenantAccess tenantAccess;

    public DeliveriesController(DeliveryLifecycleServiceImpl deliveryLifecycleService,
                                  DeliveryQueryService deliveryQueryService,
                                  DeliveryStateTransitionRepository transitionRepository,
                                  TenantAccess tenantAccess) {
        this.deliveryLifecycleService = deliveryLifecycleService;
        this.deliveryQueryService = deliveryQueryService;
        this.transitionRepository = transitionRepository;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Asigna una entrega a su conductor y cisterna.
     *
     * <p>Solo opera sobre entregas del distribuidor autenticado; las ajenas responden como no encontradas. Repetir la asignación no duplica el historial ni los eventos.</p>
     */
    @Operation(summary = "Asignar entrega",
            description = "Avanza a asignada una entrega propia del distribuidor autenticado. Repetir la operación en ese estado no genera efectos duplicados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega asignada o ya se encontraba asignada."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o pertenece a otro distribuidor."),
            @ApiResponse(responseCode = "409", description = "El estado físico actual no permite asignar la entrega.")
    })
    @PostMapping("/{deliveryId}/assign")
    public ResponseEntity<?> assign(@PathVariable Long deliveryId) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(new AssignDeliveryCommand(deliveryId)));
    }

    /**
     * Inicia una entrega en ruta.
     *
     * <p>Solo el distribuidor propietario puede iniciarla y debe estar en estado asignado.</p>
     */
    @Operation(summary = "Iniciar entrega",
            description = "Avanza a iniciada una entrega propia que actualmente está asignada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega iniciada."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o pertenece a otro distribuidor."),
            @ApiResponse(responseCode = "409", description = "El estado físico actual no permite iniciar la entrega.")
    })
    @PostMapping("/{deliveryId}/start")
    public ResponseEntity<?> start(@PathVariable Long deliveryId) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(new StartDeliveryCommand(deliveryId)));
    }

    /**
     * Marca la llegada de una entrega.
     *
     * <p>Solo el distribuidor propietario puede registrarla y la entrega debe estar en ruta.</p>
     */
    @Operation(summary = "Registrar llegada de entrega",
            description = "Avanza a llegada una entrega en ruta del distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Llegada de la entrega registrada."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o pertenece a otro distribuidor."),
            @ApiResponse(responseCode = "409", description = "El estado físico actual no permite registrar la llegada.")
    })
    @PostMapping("/{deliveryId}/arrive")
    public ResponseEntity<?> arrive(@PathVariable Long deliveryId) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(new ArriveDeliveryCommand(deliveryId)));
    }

    /**
     * Completa una entrega y registra el volumen entregado como evidencia.
     *
     * <p>Solo el distribuidor propietario puede completarla. El volumen entregado debe ser válido y también debe poder resolverse el volumen solicitado de la orden; al completar desde llegada se registra la descarga dentro de la misma transacción.</p>
     */
    @Operation(summary = "Completar entrega",
            description = "Cierra una entrega propia y registra el volumen entregado. La orden debe aportar el volumen solicitado para cerrar el historial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega completada."),
            @ApiResponse(responseCode = "400", description = "El volumen entregado no es válido como evidencia."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o pertenece a otro distribuidor."),
            @ApiResponse(responseCode = "409", description = "El estado actual impide completar la entrega o hubo una transición concurrente."),
            @ApiResponse(responseCode = "422", description = "No se pudo resolver el volumen solicitado para cerrar la entrega.")
    })
    @PostMapping("/{deliveryId}/complete")
    public ResponseEntity<?> complete(@PathVariable Long deliveryId,
                                      @RequestBody CompleteDeliveryResource resource) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(
                new CompletePhysicalDeliveryCommand(deliveryId, resource.deliveredVolume())));
    }

    /**
     * Marca una entrega como fallida.
     *
     * <p>Solo el distribuidor propietario puede hacerlo. El motivo es obligatorio y el estado fallido es terminal.</p>
     */
    @Operation(summary = "Marcar entrega fallida",
            description = "Registra un fallo terminal para la entrega propia y guarda el motivo indicado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega marcada como fallida."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o pertenece a otro distribuidor."),
            @ApiResponse(responseCode = "409", description = "El estado actual impide marcarla como fallida o hubo una transición concurrente.")
    })
    @PostMapping("/{deliveryId}/fail")
    public ResponseEntity<?> fail(@PathVariable Long deliveryId,
                                  @RequestBody FailDeliveryResource resource) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(
                new FailDeliveryCommand(deliveryId, resource.reason())));
    }

    /**
     * Cancela una entrega.
     *
     * <p>Solo el distribuidor propietario puede cancelarla. La cancelación es terminal y requiere motivo.</p>
     */
    @Operation(summary = "Cancelar entrega",
            description = "Registra la cancelación terminal de una entrega propia y guarda el motivo indicado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega cancelada."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o pertenece a otro distribuidor."),
            @ApiResponse(responseCode = "409", description = "El estado actual impide cancelarla o hubo una transición concurrente.")
    })
    @PostMapping("/{deliveryId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long deliveryId,
                                    @RequestBody FailDeliveryResource resource) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(
                new CancelDeliveryCommand(deliveryId, resource.reason())));
    }

    /**
     * Consulta una entrega y su estado físico.
     *
     * <p>Solo consulta entregas propias del distribuidor autenticado; las ajenas responden como no encontradas.</p>
     */
    @Operation(summary = "Consultar entrega",
            description = "Devuelve una entrega propia con su estado heredado y su estado físico vigente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega devuelta."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o pertenece a otro distribuidor.")
    })
    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResource> get(@PathVariable Long deliveryId) {
        if (!owns(deliveryId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return deliveryQueryService.handle(new GetDeliveryByIdQuery(deliveryId))
                .map(delivery -> new ResponseEntity<>(toResource(delivery), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Lista el historial inmutable de estados de una entrega.
     *
     * <p>Solo lo consulta el distribuidor propietario. Cada registro conserva la transición física, la versión del agregado y su instante para reconstruir la historia.</p>
     */
    @Operation(summary = "Listar transiciones de una entrega",
            description = "Devuelve el historial inmutable de transiciones físicas de una entrega propia, con sus versiones e instantes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transiciones de estado devueltas."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o pertenece a otro distribuidor.")
    })
    @GetMapping("/{deliveryId}/transitions")
    public ResponseEntity<List<DeliveryTransitionResource>> transitions(@PathVariable Long deliveryId) {
        if (!owns(deliveryId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var resources = transitionRepository.findByDeliveryId(deliveryId).stream()
                .map(transition -> new DeliveryTransitionResource(transition.id(), transition.fromState(),
                        transition.toState(), transition.aggregateVersion(), transition.occurredAt()))
                .toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    private ResponseEntity<?> advance(Long deliveryId, Supplier<Result<Delivery, ApplicationError>> action) {
        if (!owns(deliveryId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                action.get(), DeliveriesController::toResource, HttpStatus.OK);
    }

    private boolean owns(Long deliveryId) {
        var providerId = tenantAccess.currentProviderId();
        return providerId.isPresent()
                && deliveryQueryService.handle(new GetDeliveryByIdQuery(deliveryId))
                .map(delivery -> providerId.get().equals(delivery.getProviderId()))
                .orElse(false);
    }

    private static DeliveryResource toResource(Delivery delivery) {
        return new DeliveryResource(delivery.getId(), delivery.getOrderId(), delivery.getProviderId(),
                delivery.getDriverId(), delivery.getVehicleId(), delivery.getStatus(),
                delivery.currentPhysicalState(), delivery.getRequestedVolume(), delivery.getDeliveredVolume(),
                delivery.getDispatchedAt(), delivery.getStartedAt(), delivery.getArrivedAt(),
                delivery.getDeliveringAt(), delivery.getDeliveredAt(), delivery.getNotes(),
                delivery.getVersion());
    }
}
