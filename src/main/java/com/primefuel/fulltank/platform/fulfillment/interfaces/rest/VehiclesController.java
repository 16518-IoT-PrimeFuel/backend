package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateTankerCommand;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.VehicleResource;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * v1 vehicle adapter (S12/T12-A). The legacy route name stays, but the data flows through the public
 * {@code fleet.api} (as a tanker) and authorization through {@code iam.api.TenantAccess}. Delete is a
 * soft-disable.
 */
@RestController
@RequestMapping("/api/v1/vehicles")
public class VehiclesController {

    private final FleetCatalog fleetCatalog;
    private final FleetRegistry fleetRegistry;
    private final TenantAccess tenantAccess;

    public VehiclesController(FleetCatalog fleetCatalog, FleetRegistry fleetRegistry, TenantAccess tenantAccess) {
        this.fleetCatalog = fleetCatalog;
        this.fleetRegistry = fleetRegistry;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping
    @PreAuthorize("@tenantAccess.ownsProvider(#providerId)")
    public List<VehicleResource> getByProvider(@RequestParam Long providerId) {
        return fleetCatalog.listTankers(providerId).stream().map(VehiclesController::toResource).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResource> getById(@PathVariable Long id) {
        return fleetCatalog.findTanker(id)
                .filter(tanker -> tenantAccess.ownsProvider(tanker.providerId()))
                .map(VehiclesController::toResource)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("@tenantAccess.ownsProvider(#resource.providerId())")
    public ResponseEntity<VehicleResource> create(@RequestBody VehicleResource resource) {
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(
                resource.providerId(), resource.licensePlate(), resource.brand(), resource.model(),
                resource.capacity(), defaultUnit(resource.unit()), defaultStatus(resource.status())));
        return result.toOptional()
                .map(VehiclesController::toResource)
                .map(created -> new ResponseEntity<>(created, HttpStatus.CREATED))
                .orElse(ResponseEntity.badRequest().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<VehicleResource> update(@PathVariable Long id, @RequestBody VehicleResource resource) {
        var existing = fleetCatalog.findTanker(id);
        if (existing.isEmpty() || !tenantAccess.ownsProvider(existing.get().providerId())) {
            return ResponseEntity.notFound().build();
        }
        Long providerId = resource.providerId() != null ? resource.providerId() : existing.get().providerId();
        if (!tenantAccess.ownsProvider(providerId)) return ResponseEntity.notFound().build();
        var result = fleetRegistry.updateTanker(new UpdateTankerCommand(id, resource.licensePlate(),
                resource.brand(), resource.model(), resource.capacity(),
                defaultUnit(resource.unit()), defaultStatus(resource.status())));
        return result.toOptional()
                .map(VehiclesController::toResource)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var existing = fleetCatalog.findTanker(id);
        if (existing.isEmpty() || !tenantAccess.ownsProvider(existing.get().providerId())) {
            return ResponseEntity.notFound().build();
        }
        fleetRegistry.deactivateTanker(id);
        return ResponseEntity.noContent().build();
    }

    private static String defaultUnit(String unit) {
        return unit == null || unit.isBlank() ? "LITERS" : unit;
    }

    private static String defaultStatus(String status) {
        return status == null || status.isBlank() ? "AVAILABLE" : status;
    }

    private static VehicleResource toResource(FleetCatalog.TankerSnapshot tanker) {
        return new VehicleResource(tanker.id(), tanker.providerId(), tanker.licensePlate(), tanker.brand(),
                tanker.model(), tanker.capacity(), tanker.unit(), tanker.status());
    }
}
