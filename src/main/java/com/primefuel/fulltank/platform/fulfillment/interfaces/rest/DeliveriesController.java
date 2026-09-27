package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.commandservices.DeliveryCommandService;
import com.primefuel.fulltank.platform.fulfillment.application.queryservices.DeliveryQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CompleteDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.DispatchDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.FailDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetAllDeliveriesQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByIdQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByOrderIdQuery;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.CreateDeliveryResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.DeliveryResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.FailDeliveryResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.transform.CreateDeliveryCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.transform.DeliveryResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrderByIdQuery;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Entregas", description = "Creación, consulta y actualización de entregas de combustible")
public class DeliveriesController {

    private final DeliveryCommandService deliveryCommandService;
    private final DeliveryQueryService deliveryQueryService;
    private final FuelOrderQueryService fuelOrderQueryService;
    private final TenantAccess tenantAccess;

    public DeliveriesController(DeliveryCommandService deliveryCommandService,
                                DeliveryQueryService deliveryQueryService,
                                FuelOrderQueryService fuelOrderQueryService,
                                TenantAccess tenantAccess) {
        this.deliveryCommandService = deliveryCommandService;
        this.deliveryQueryService = deliveryQueryService;
        this.fuelOrderQueryService = fuelOrderQueryService;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Crea una entrega para una orden del distribuidor autenticado.
     *
     * <p>El distribuidor del cuerpo debe coincidir con el tenant autenticado; el conductor, la cisterna y
     * la orden deben pertenecerle. También se exige disponibilidad, capacidad y existencias suficientes.
     * La operación asigna conductor y cisterna, descuenta existencias y despacha la orden en una transacción.</p>
     */
    @Operation(summary = "Crear entrega",
            description = "Crea y despacha una entrega del distribuidor tras validar la propiedad, disponibilidad de flota, capacidad de la cisterna y existencias.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entrega creada y orden despachada."),
            @ApiResponse(responseCode = "403", description = "El tenant autenticado no es propietario del distribuidor indicado."),
            @ApiResponse(responseCode = "404", description = "No se encontró para el distribuidor el conductor, la cisterna o la orden."),
            @ApiResponse(responseCode = "409", description = "La flota no está disponible, la capacidad o las existencias son insuficientes, o la orden ya tiene una entrega.")
    })
    @PostMapping
    @PreAuthorize("@tenantAccess.ownsProvider(#resource.providerId())")
    public ResponseEntity<?> createDelivery(@RequestBody CreateDeliveryResource resource) {
        var command = CreateDeliveryCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = deliveryCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                DeliveryResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Despacha una entrega.
     *
     * <p>Solo el tenant propietario puede avanzar la entrega. Una entrega ajena o inexistente se informa
     * como no encontrada; el estado físico debe permitir el despacho.</p>
     */
    @Operation(summary = "Despachar entrega",
            description = "Avanza la entrega del distribuidor autenticado al estado de despacho cuando la transición física es válida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega despachada."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o no pertenece al distribuidor autenticado."),
            @ApiResponse(responseCode = "409", description = "El estado físico actual no permite despachar la entrega.")
    })
    @PostMapping("/{deliveryId}/dispatch")
    public ResponseEntity<?> dispatchDelivery(@PathVariable Long deliveryId) {
        if (!ownsDeliveryAsProvider(deliveryId)) return ResponseEntity.notFound().build();
        var result = deliveryCommandService.handle(new DispatchDeliveryCommand(deliveryId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                DeliveryResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Completa una entrega.
     *
     * <p>Solo el tenant propietario puede cerrarla. Como el contrato v1 no recibe volumen entregado,
     * se usa la cantidad solicitada por la orden como evidencia y se registran los estados intermedios
     * necesarios para respetar las invariantes de la máquina física.</p>
     */
    @Operation(summary = "Completar entrega",
            description = "Cierra la entrega del distribuidor autenticado usando como evidencia el volumen solicitado en la orden.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega completada."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o no pertenece al distribuidor autenticado."),
            @ApiResponse(responseCode = "409", description = "El estado físico actual no permite completar la entrega."),
            @ApiResponse(responseCode = "422", description = "No se pudo obtener de la orden el volumen requerido como evidencia de cierre.")
    })
    @PostMapping("/{deliveryId}/complete")
    public ResponseEntity<?> completeDelivery(@PathVariable Long deliveryId) {
        if (!ownsDeliveryAsProvider(deliveryId)) return ResponseEntity.notFound().build();
        var result = deliveryCommandService.handle(new CompleteDeliveryCommand(deliveryId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                DeliveryResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Registra el fallo de una entrega.
     *
     * <p>Solo el tenant propietario puede marcarla como fallida; se registra el motivo y se valida la transición física.</p>
     */
    @Operation(summary = "Marcar entrega como fallida",
            description = "Registra el motivo y cambia al estado fallido una entrega del distribuidor autenticado si la transición es válida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega marcada como fallida."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o no pertenece al distribuidor autenticado."),
            @ApiResponse(responseCode = "409", description = "El estado físico actual no permite marcar la entrega como fallida.")
    })
    @PostMapping("/{deliveryId}/fail")
    public ResponseEntity<?> failDelivery(@PathVariable Long deliveryId,
                                          @RequestBody FailDeliveryResource resource) {
        if (!ownsDeliveryAsProvider(deliveryId)) return ResponseEntity.notFound().build();
        var result = deliveryCommandService.handle(new FailDeliveryCommand(deliveryId, resource.reason()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                DeliveryResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Lista todas las entregas de la plataforma.
     *
     * <p>Disponible únicamente para usuarios con autoridad ROLE_ADMIN.</p>
     */
    @Operation(summary = "Listar todas las entregas",
            description = "Devuelve todas las entregas registradas; requiere autoridad administrativa ROLE_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de entregas."),
            @ApiResponse(responseCode = "403", description = "El usuario no cuenta con autoridad ROLE_ADMIN.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<DeliveryResource>> getAllDeliveries() {
        var deliveries = deliveryQueryService.handle(new GetAllDeliveriesQuery());
        var resources = deliveries.stream().map(DeliveryResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Lista las entregas de un distribuidor.
     *
     * <p>El tenant autenticado solo puede consultar sus propias entregas.</p>
     */
    @Operation(summary = "Listar entregas por distribuidor",
            description = "Devuelve las entregas del distribuidor indicado cuando coincide con el tenant autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de entregas del distribuidor."),
            @ApiResponse(responseCode = "403", description = "El tenant autenticado no es propietario del distribuidor solicitado.")
    })
    @GetMapping("/provider/{providerId}")
    @PreAuthorize("@tenantAccess.ownsProvider(#providerId)")
    public ResponseEntity<List<DeliveryResource>> getDeliveriesByProvider(@PathVariable Long providerId) {
        var resources = deliveryQueryService.handle(new GetAllDeliveriesQuery()).stream()
                .filter(delivery -> providerId.equals(delivery.getProviderId()))
                .map(DeliveryResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    /**
     * Consulta una entrega por su identificador.
     *
     * <p>Puede consultarla el tenant del distribuidor o la empresa compradora de la orden; para otros usuarios se responde como no encontrada.</p>
     */
    @Operation(summary = "Consultar entrega por identificador",
            description = "Devuelve la entrega cuando el usuario pertenece al distribuidor o a la empresa compradora de la orden.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega devuelta."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o no es visible para el usuario.")
    })
    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResource> getDeliveryById(@PathVariable Long deliveryId) {
        var result = deliveryQueryService.handle(new GetDeliveryByIdQuery(deliveryId))
                .filter(this::ownsDelivery);
        return result.map(d -> new ResponseEntity<>(
                        DeliveryResourceFromEntityAssembler.toResourceFromEntity(d), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Consulta la entrega asociada a una orden.
     *
     * <p>Puede consultarla el tenant del distribuidor o la empresa compradora; para otros usuarios se responde como no encontrada.</p>
     */
    @Operation(summary = "Consultar entrega de una orden",
            description = "Devuelve la entrega asociada cuando el usuario pertenece al distribuidor o a la empresa compradora de la orden.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrega devuelta."),
            @ApiResponse(responseCode = "404", description = "La orden no tiene entrega o esta no es visible para el usuario.")
    })
    @GetMapping("/order/{orderId}")
    public ResponseEntity<DeliveryResource> getDeliveryByOrder(@PathVariable Long orderId) {
        var result = deliveryQueryService.handle(new GetDeliveryByOrderIdQuery(orderId));
        return result.filter(this::ownsDelivery).map(d -> new ResponseEntity<>(
                        DeliveryResourceFromEntityAssembler.toResourceFromEntity(d), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    private boolean ownsDeliveryAsProvider(Long deliveryId) {
        return deliveryQueryService.handle(new GetDeliveryByIdQuery(deliveryId))
                .filter(delivery -> tenantAccess.ownsProvider(delivery.getProviderId()))
                .isPresent();
    }

    private boolean ownsDelivery(com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery delivery) {
        if (tenantAccess.ownsProvider(delivery.getProviderId())) return true;
        return fuelOrderQueryService.handle(new GetFuelOrderByIdQuery(delivery.getOrderId()))
                .filter(order -> tenantAccess.ownsCompany(order.getCompanyId()))
                .isPresent();
    }
}
