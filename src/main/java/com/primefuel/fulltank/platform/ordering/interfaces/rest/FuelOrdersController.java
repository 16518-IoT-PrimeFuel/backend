package com.primefuel.fulltank.platform.ordering.interfaces.rest;

import com.primefuel.fulltank.platform.ordering.application.commandservices.FuelOrderCommandService;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CancelFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.ConfirmFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetAllFuelOrdersQuery;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrderByIdQuery;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrdersByCompanyIdQuery;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrdersByProviderIdQuery;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.CreateFuelOrderResource;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.FuelOrderResource;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.transform.CreateFuelOrderCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.transform.FuelOrderResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services.CurrentUserAccess;
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
@RequestMapping(value = "/api/v1/fuel-orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Órdenes de combustible", description = "Creación, consulta y gestión de órdenes entre empresas compradoras y distribuidores")
public class FuelOrdersController {

    private final FuelOrderCommandService fuelOrderCommandService;
    private final FuelOrderQueryService fuelOrderQueryService;
    private final CurrentUserAccess currentUserAccess;

    public FuelOrdersController(FuelOrderCommandService fuelOrderCommandService,
                                FuelOrderQueryService fuelOrderQueryService,
                                CurrentUserAccess currentUserAccess) {
        this.fuelOrderCommandService = fuelOrderCommandService;
        this.fuelOrderQueryService = fuelOrderQueryService;
        this.currentUserAccess = currentUserAccess;
    }

    /**
     * Crea una orden de combustible para la empresa compradora autenticada.
     *
     * <p>La empresa debe pertenecer al usuario; el producto al distribuidor y el equipo a la empresa indicada.</p>
     */
    @Operation(summary = "Crear orden de combustible",
            description = "Registra una orden para la empresa del usuario y valida la relación entre distribuidor, producto y equipo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orden de combustible creada."),
            @ApiResponse(responseCode = "403", description = "La empresa no pertenece al usuario o el producto/equipo no corresponde al tenant indicado."),
            @ApiResponse(responseCode = "404", description = "No existe el producto de combustible o el equipo indicado.")
    })
    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsCompany(#resource.companyId())")
    public ResponseEntity<?> createFuelOrder(@RequestBody CreateFuelOrderResource resource) {
        var command = CreateFuelOrderCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = fuelOrderCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                FuelOrderResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Confirma una orden de combustible.
     *
     * <p>Solo la empresa compradora propietaria puede confirmarla. El estado actual no bloquea esta transición.</p>
     */
    @Operation(summary = "Confirmar orden de combustible",
            description = "Registra la confirmación solicitada por la empresa compradora propietaria de la orden.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden de combustible confirmada."),
            @ApiResponse(responseCode = "404", description = "La orden no existe o pertenece a otra empresa compradora.")
    })
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<?> confirmOrder(@PathVariable Long orderId) {
        if (!ownsOrderAsBuyer(orderId)) return ResponseEntity.notFound().build();
        var result = fuelOrderCommandService.handle(new ConfirmFuelOrderCommand(orderId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                FuelOrderResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Cancela una orden de combustible.
     *
     * <p>Puede cancelarla la empresa compradora o el distribuidor propietario. El estado actual no bloquea esta transición.</p>
     */
    @Operation(summary = "Cancelar orden de combustible",
            description = "Registra la cancelación solicitada por la empresa compradora o el distribuidor asociado a la orden.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden de combustible cancelada."),
            @ApiResponse(responseCode = "404", description = "La orden no existe o no pertenece al usuario.")
    })
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId) {
        if (!ownsOrder(orderId)) return ResponseEntity.notFound().build();
        var result = fuelOrderCommandService.handle(new CancelFuelOrderCommand(orderId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                FuelOrderResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Lista todas las órdenes de combustible de la plataforma.
     *
     * <p>Requiere la autoridad ROLE_ADMIN.</p>
     */
    @Operation(summary = "Listar todas las órdenes de combustible",
            description = "Devuelve todas las órdenes registradas; solo está disponible para administradores con autoridad ROLE_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Órdenes de combustible devueltas."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene la autoridad ROLE_ADMIN.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<FuelOrderResource>> getAllOrders() {
        var orders = fuelOrderQueryService.handle(new GetAllFuelOrdersQuery());
        var resources = orders.stream().map(FuelOrderResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Consulta una orden de combustible por identificador.
     *
     * <p>Puede verla la empresa compradora o el distribuidor asociado; las órdenes ajenas o inexistentes responden como no encontradas.</p>
     */
    @Operation(summary = "Consultar orden por identificador",
            description = "Devuelve la orden indicada a su empresa compradora o al distribuidor asociado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden de combustible devuelta."),
            @ApiResponse(responseCode = "404", description = "La orden no existe o no pertenece al usuario.")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<FuelOrderResource> getOrderById(@PathVariable Long orderId) {
        var result = fuelOrderQueryService.handle(new GetFuelOrderByIdQuery(orderId))
                .filter(order -> currentUserAccess.ownsCompanyOrProvider(order.getCompanyId(), order.getProviderId()));
        return result.map(o -> new ResponseEntity<>(
                        FuelOrderResourceFromEntityAssembler.toResourceFromEntity(o), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Lista las órdenes de una empresa compradora.
     *
     * <p>Solo la empresa propietaria puede consultar esta colección.</p>
     */
    @Operation(summary = "Listar órdenes por empresa compradora",
            description = "Devuelve las órdenes de la empresa indicada cuando pertenece al usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Órdenes de combustible devueltas."),
            @ApiResponse(responseCode = "403", description = "La empresa solicitada no pertenece al usuario.")
    })
    @GetMapping("/company/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<List<FuelOrderResource>> getOrdersByCompany(@PathVariable Long companyId) {
        var orders = fuelOrderQueryService.handle(new GetFuelOrdersByCompanyIdQuery(companyId));
        var resources = orders.stream().map(FuelOrderResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Lista las órdenes de un distribuidor.
     *
     * <p>Solo el distribuidor propietario puede consultar esta colección.</p>
     */
    @Operation(summary = "Listar órdenes por distribuidor",
            description = "Devuelve las órdenes del distribuidor indicado cuando el usuario es propietario de ese tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Órdenes de combustible devueltas."),
            @ApiResponse(responseCode = "403", description = "El distribuidor solicitado no pertenece al usuario.")
    })
    @GetMapping("/provider/{providerId}")
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public ResponseEntity<List<FuelOrderResource>> getOrdersByProvider(@PathVariable Long providerId) {
        var orders = fuelOrderQueryService.handle(new GetFuelOrdersByProviderIdQuery(providerId));
        var resources = orders.stream().map(FuelOrderResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    private boolean ownsOrder(Long orderId) {
        return fuelOrderQueryService.handle(new GetFuelOrderByIdQuery(orderId))
                .filter(order -> currentUserAccess.ownsCompanyOrProvider(
                        order.getCompanyId(), order.getProviderId()))
                .isPresent();
    }

    private boolean ownsOrderAsBuyer(Long orderId) {
        return fuelOrderQueryService.handle(new GetFuelOrderByIdQuery(orderId))
                .filter(order -> currentUserAccess.ownsCompany(order.getCompanyId()))
                .isPresent();
    }
}
