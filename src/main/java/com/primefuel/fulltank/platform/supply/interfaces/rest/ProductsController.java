package com.primefuel.fulltank.platform.supply.interfaces.rest;

import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.supply.api.SupplyCatalog;
import com.primefuel.fulltank.platform.supply.interfaces.rest.resources.ProductResource;
import com.primefuel.fulltank.platform.supply.interfaces.rest.transform.ProductResourceFromSnapshotAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v2/products", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Oferta de combustible", description = "Catálogo de productos del distribuidor logístico autenticado")
public class ProductsController {

    private final SupplyCatalog supplyCatalog;
    private final TenantAccess tenantAccess;

    public ProductsController(SupplyCatalog supplyCatalog, TenantAccess tenantAccess) {
        this.supplyCatalog = supplyCatalog;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Lista los productos de combustible del distribuidor logístico autenticado.
     *
     * <p>El distribuidor se obtiene de la identidad autenticada. El parámetro permite excluir productos inactivos; otros tenants no pueden consultarse.</p>
     */
    @Operation(summary = "Listar productos de combustible",
            description = "Devuelve el catálogo del distribuidor autenticado. `activeOnly` permite omitir productos inactivos; requiere identidad de distribuidor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Productos devueltos, posiblemente una lista vacía."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene una identidad de distribuidor logístico.")
    })
    @GetMapping
    public ResponseEntity<List<ProductResource>> listProducts(
            @RequestParam(name = "activeOnly", defaultValue = "true") boolean activeOnly) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var resources = supplyCatalog.listForTenant(providerId.get(), activeOnly).stream()
                .map(ProductResourceFromSnapshotAssembler::toResourceFromSnapshot)
                .toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Consulta un producto de combustible por identificador.
     *
     * <p>Solo se muestran productos del distribuidor autenticado; los inexistentes o ajenos responden como no encontrados.</p>
     */
    @Operation(summary = "Consultar producto de combustible",
            description = "Devuelve el producto indicado si pertenece al distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto de combustible devuelto."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene una identidad de distribuidor logístico."),
            @ApiResponse(responseCode = "404", description = "El producto no existe o pertenece a otro distribuidor.")
    })
    @GetMapping("/{fuelProductId}")
    public ResponseEntity<ProductResource> getProduct(@PathVariable Long fuelProductId) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return supplyCatalog.findForTenant(providerId.get(), fuelProductId)
                .map(snapshot -> new ResponseEntity<>(
                        ProductResourceFromSnapshotAssembler.toResourceFromSnapshot(snapshot), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
