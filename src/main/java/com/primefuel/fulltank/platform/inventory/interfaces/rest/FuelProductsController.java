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
@Tag(name = "Fuel Products", description = "Inventory management endpoints")
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
     * Creates a fuel product for a provider tenant.
     *
     * <p>The provider id in the body must be the caller's own provider tenant.</p>
     */
    @Operation(summary = "Create a fuel product",
            description = "Creates a fuel product owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Fuel product created."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the provider in the request body.")
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
     * Updates the stock of a fuel product.
     *
     * <p>Only the owning provider tenant may change the stock. A product that does not exist or belongs
     * to another tenant is reported as not found.</p>
     */
    @Operation(summary = "Update fuel product stock",
            description = "Sets the available stock of a fuel product owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock updated."),
            @ApiResponse(responseCode = "404", description = "Fuel product does not exist or belongs to another provider tenant.")
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
     * Lists every fuel product in the platform.
     *
     * <p>Restricted to the buyer role (the procurement side).</p>
     */
    @Operation(summary = "List all fuel products",
            description = "Returns every registered fuel product. Restricted to buyers.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel products returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not hold the buyer role.")
    })
    @GetMapping
    @PreAuthorize("@tenantAccess.isBuyerRole()")
    public ResponseEntity<List<FuelProductResource>> getAllFuelProducts() {
        var products = fuelProductQueryService.handle(new GetAllFuelProductsQuery());
        var resources = products.stream().map(FuelProductResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Retrieves a single fuel product.
     *
     * <p>Readable by any buyer, or by the provider tenant that owns the product; anything else is
     * reported as not found.</p>
     */
    @Operation(summary = "Get a fuel product by id",
            description = "Returns the fuel product identified by the path id to a buyer or its owning provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel product returned."),
            @ApiResponse(responseCode = "404", description = "Fuel product does not exist or is not visible to the caller.")
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
     * Lists the fuel products of a provider tenant.
     *
     * <p>Available to any buyer or to the owning provider tenant.</p>
     */
    @Operation(summary = "List fuel products by provider",
            description = "Returns all fuel products of the given provider to a buyer or to its owning provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel products returned."),
            @ApiResponse(responseCode = "403", description = "Caller is neither a buyer nor the owner of the provider tenant.")
    })
    @GetMapping("/provider/{providerId}")
    @PreAuthorize("@tenantAccess.isBuyerRole() or @tenantAccess.ownsProvider(#providerId)")
    public ResponseEntity<List<FuelProductResource>> getFuelProductsByProvider(@PathVariable Long providerId) {
        var products = fuelProductQueryService.handle(new GetFuelProductsByProviderIdQuery(providerId));
        var resources = products.stream().map(FuelProductResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Updates a fuel product.
     *
     * <p>Only the owning provider tenant may update it; a product that does not exist or belongs to
     * another tenant is reported as not found.</p>
     */
    @Operation(summary = "Update a fuel product",
            description = "Applies field changes to a fuel product owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel product updated."),
            @ApiResponse(responseCode = "404", description = "Fuel product does not exist or belongs to another provider tenant.")
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
     * Deletes a fuel product.
     *
     * <p>Only the owning provider tenant may delete it. A product still referenced by existing
     * requests or orders cannot be removed and is reported as a conflict.</p>
     */
    @Operation(summary = "Delete a fuel product",
            description = "Removes a fuel product owned by the caller's provider tenant when it is not referenced by other records.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Fuel product deleted; no content returned."),
            @ApiResponse(responseCode = "404", description = "Fuel product does not exist or belongs to another provider tenant."),
            @ApiResponse(responseCode = "409", description = "Fuel product is still referenced by existing requests or orders.")
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
