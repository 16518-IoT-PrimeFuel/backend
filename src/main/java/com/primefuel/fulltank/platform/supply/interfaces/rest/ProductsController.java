package com.primefuel.fulltank.platform.supply.interfaces.rest;

import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.supply.api.SupplyCatalog;
import com.primefuel.fulltank.platform.supply.interfaces.rest.resources.ProductResource;
import com.primefuel.fulltank.platform.supply.interfaces.rest.transform.ProductResourceFromSnapshotAssembler;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v2/products", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Supply", description = "Tenant-scoped supply catalog (v2)")
public class ProductsController {

    private final SupplyCatalog supplyCatalog;
    private final TenantAccess tenantAccess;

    public ProductsController(SupplyCatalog supplyCatalog, TenantAccess tenantAccess) {
        this.supplyCatalog = supplyCatalog;
        this.tenantAccess = tenantAccess;
    }

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
