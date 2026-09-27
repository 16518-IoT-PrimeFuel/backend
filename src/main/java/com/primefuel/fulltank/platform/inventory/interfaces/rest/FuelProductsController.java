package com.primefuel.fulltank.platform.inventory.interfaces.rest;

import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.application.queryservices.FuelProductQueryService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.DeleteFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.queries.GetAllFuelProductsQuery;
import com.primefuel.fulltank.platform.inventory.domain.model.queries.GetFuelProductByIdQuery;
import com.primefuel.fulltank.platform.inventory.domain.model.queries.GetFuelProductsByProviderIdQuery;
import com.primefuel.fulltank.platform.inventory.domain.model.aggregates.FuelProduct;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.resources.CreateFuelProductResource;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.resources.FuelProductResource;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.resources.UpdateFuelProductResource;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.resources.UpdateFuelProductStockResource;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.transform.CreateFuelProductCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.transform.FuelProductResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.transform.UpdateFuelProductCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.transform.UpdateFuelProductStockCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
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
@RequestMapping(value = "/api/v1/fuel-products", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Productos de combustible", description = "Catálogo, existencias y disponibilidad por distribuidor")
public class FuelProductsController {

    private final FuelProductCommandService fuelProductCommandService;
    private final FuelProductQueryService fuelProductQueryService;
    private final TenantAccess tenantAccess;

    public FuelProductsController(FuelProductCommandService fuelProductCommandService,
                                  FuelProductQueryService fuelProductQueryService,
                                  TenantAccess tenantAccess) {
        this.fuelProductCommandService = fuelProductCommandService;
        this.fuelProductQueryService = fuelProductQueryService;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Registra un producto de combustible para un distribuidor.
     *
     * <p>El distribuidor indicado debe coincidir con el tenant del usuario autenticado.</p>
     */
    @Operation(summary = "Crear producto de combustible",
            description = "Registra un producto en el catálogo del distribuidor autenticado y verifica que el tenant del cuerpo sea propio.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Producto de combustible creado."),
            @ApiResponse(responseCode = "403", description = "El distribuidor indicado en la solicitud no pertenece al usuario.")
    })
    @PostMapping
    @PreAuthorize("@tenantAccess.ownsProvider(#resource.providerId())")
    public ResponseEntity<?> createFuelProduct(@RequestBody CreateFuelProductResource resource) {
        var command = CreateFuelProductCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = fuelProductCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                FuelProductResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Actualiza las existencias disponibles de un producto.
     *
     * <p>Solo el distribuidor propietario puede modificar las existencias. Los productos inexistentes o ajenos se informan como no encontrados.</p>
     */
    @Operation(summary = "Actualizar existencias del producto",
            description = "Establece la cantidad disponible para un producto propio del distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Existencias actualizadas."),
            @ApiResponse(responseCode = "404", description = "El producto no existe o pertenece a otro distribuidor.")
    })
    @PostMapping("/{fuelProductId}/update-stock")
    public ResponseEntity<?> updateStock(@PathVariable Long fuelProductId,
                                         @RequestBody UpdateFuelProductStockResource resource) {
        if (!ownsProduct(fuelProductId)) return ResponseEntity.notFound().build();
        var command = UpdateFuelProductStockCommandFromResourceAssembler.toCommandFromResource(fuelProductId, resource);
        var result = fuelProductCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                FuelProductResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Lista los productos visibles para compradores.
     *
     * <p>Requiere el rol comprador y devuelve el catálogo registrado en la plataforma.</p>
     */
    @Operation(summary = "Listar productos de combustible",
            description = "Devuelve los productos registrados en la plataforma para consulta de compradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Productos de combustible devueltos."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene el rol comprador.")
    })
    @GetMapping
    @PreAuthorize("@tenantAccess.isBuyerRole()")
    public ResponseEntity<List<FuelProductResource>> getAllFuelProducts() {
        var products = fuelProductQueryService.handle(new GetAllFuelProductsQuery());
        var resources = products.stream().map(FuelProductResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Consulta un producto de combustible por identificador.
     *
     * <p>Pueden consultarlo compradores y el distribuidor propietario; los productos ajenos o inexistentes responden como no encontrados.</p>
     */
    @Operation(summary = "Consultar producto por identificador",
            description = "Devuelve el producto indicado a un comprador o al distribuidor propietario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto de combustible devuelto."),
            @ApiResponse(responseCode = "404", description = "El producto no existe o no es visible para el usuario.")
    })
    @GetMapping("/{fuelProductId}")
    public ResponseEntity<FuelProductResource> getFuelProductById(@PathVariable Long fuelProductId) {
        var result = fuelProductQueryService.handle(new GetFuelProductByIdQuery(fuelProductId))
                .filter(product -> tenantAccess.isBuyerRole()
                        || tenantAccess.ownsProvider(product.getProviderId()));
        return result.map(p -> new ResponseEntity<>(
                        FuelProductResourceFromEntityAssembler.toResourceFromEntity(p), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Lista los productos de un distribuidor.
     *
     * <p>Disponible para compradores y para el distribuidor propietario del catálogo.</p>
     */
    @Operation(summary = "Listar productos por distribuidor",
            description = "Devuelve los productos del distribuidor indicado a compradores o al mismo distribuidor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Productos de combustible devueltos."),
            @ApiResponse(responseCode = "403", description = "El usuario no es comprador ni propietario del distribuidor indicado.")
    })
    @GetMapping("/provider/{providerId}")
    @PreAuthorize("@tenantAccess.isBuyerRole() or @tenantAccess.ownsProvider(#providerId)")
    public ResponseEntity<List<FuelProductResource>> getFuelProductsByProvider(@PathVariable Long providerId) {
        var products = fuelProductQueryService.handle(new GetFuelProductsByProviderIdQuery(providerId));
        var resources = products.stream().map(FuelProductResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Actualiza los datos editables de un producto.
     *
     * <p>Solo el distribuidor propietario puede modificarlo; un producto inexistente o ajeno responde como no encontrado.</p>
     */
    @Operation(summary = "Actualizar producto de combustible",
            description = "Aplica los cambios permitidos al producto propio del distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto de combustible actualizado."),
            @ApiResponse(responseCode = "404", description = "El producto no existe o pertenece a otro distribuidor.")
    })
    @PutMapping("/{fuelProductId}")
    public ResponseEntity<?> updateFuelProduct(@PathVariable Long fuelProductId,
                                               @RequestBody UpdateFuelProductResource resource) {
        if (!ownsProduct(fuelProductId)) return ResponseEntity.notFound().build();
        var command = UpdateFuelProductCommandFromResourceAssembler.toCommandFromResource(fuelProductId, resource);
        var result = fuelProductCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                FuelProductResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Elimina un producto de combustible.
     *
     * <p>Solo el distribuidor propietario puede eliminarlo. Se conserva el producto cuando existen solicitudes u órdenes que lo referencian.</p>
     */
    @Operation(summary = "Eliminar producto de combustible",
            description = "Elimina el producto del catálogo propio solo cuando ninguna solicitud ni orden existente lo utiliza.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Producto eliminado sin cuerpo de respuesta."),
            @ApiResponse(responseCode = "404", description = "El producto no existe o pertenece a otro distribuidor."),
            @ApiResponse(responseCode = "409", description = "El producto sigue asociado a solicitudes u órdenes existentes.")
    })
    @DeleteMapping("/{fuelProductId}")
    public ResponseEntity<?> deleteFuelProduct(@PathVariable Long fuelProductId) {
        if (!ownsProduct(fuelProductId)) return ResponseEntity.notFound().build();
        var result = fuelProductCommandService.handle(new DeleteFuelProductCommand(fuelProductId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                ignored -> null,
                HttpStatus.NO_CONTENT);
    }

    private boolean ownsProduct(Long fuelProductId) {
        return fuelProductQueryService.handle(new GetFuelProductByIdQuery(fuelProductId))
                .map(FuelProduct::getProviderId)
                .filter(tenantAccess::ownsProvider)
                .isPresent();
    }
}
