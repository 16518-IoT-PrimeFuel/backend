package com.primefuel.fulltank.platform.inventory.interfaces.rest;

import com.primefuel.fulltank.platform.inventory.application.queryservices.FuelProductQueryService;
import com.primefuel.fulltank.platform.inventory.domain.model.aggregates.FuelProduct;
import com.primefuel.fulltank.platform.inventory.domain.model.queries.GetAllFuelProductsQuery;
import com.primefuel.fulltank.platform.inventory.domain.model.queries.GetFuelProductByIdQuery;
import com.primefuel.fulltank.platform.inventory.domain.model.queries.GetFuelProductsByProviderIdQuery;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.resources.FuelProductResource;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.transform.FuelProductResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.interfaces.acl.TenantAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/products")
public class SupplyProductsV2Controller {
    private final FuelProductQueryService products;
    private final TenantAccess access;

    public SupplyProductsV2Controller(FuelProductQueryService products, TenantAccess access) {
        this.products = products;
        this.access = access;
    }

    @GetMapping
    public ResponseEntity<List<FuelProductResource>> list(@RequestParam(required = false) Long providerId) {
        if (providerId == null && !access.isBuyerRole()) return ResponseEntity.status(403).build();
        if (providerId != null && !access.isBuyerRole() && !access.ownsProvider(providerId)) {
            return ResponseEntity.notFound().build();
        }
        var source = providerId == null
                ? products.handle(new GetAllFuelProductsQuery())
                : products.handle(new GetFuelProductsByProviderIdQuery(providerId));
        return ResponseEntity.ok(active(source));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<FuelProductResource> get(@PathVariable Long productId) {
        return products.handle(new GetFuelProductByIdQuery(productId))
                .filter(this::visible)
                .map(FuelProductResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean visible(FuelProduct product) {
        return Boolean.TRUE.equals(product.getActive())
                && (access.isBuyerRole() || access.ownsProvider(product.getProviderId()));
    }

    private List<FuelProductResource> active(List<FuelProduct> source) {
        return source.stream().filter(this::visible)
                .map(FuelProductResourceFromEntityAssembler::toResourceFromEntity).toList();
    }
}
