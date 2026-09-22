package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateDriverCommand;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.DriverResource;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * v1 driver adapter (S12/T12-A). It is a thin compatibility surface: it no longer touches the driver
 * repository, only the public {@code fleet.api}, and it authorizes through the public
 * {@code iam.api.TenantAccess} seam. Delete is now a soft-disable (the row is retained, status 204).
 */
@RestController
@RequestMapping("/api/v1/drivers")
public class DriversController {

    private final FleetCatalog fleetCatalog;
    private final FleetRegistry fleetRegistry;
    private final TenantAccess tenantAccess;

    public DriversController(FleetCatalog fleetCatalog, FleetRegistry fleetRegistry, TenantAccess tenantAccess) {
        this.fleetCatalog = fleetCatalog;
        this.fleetRegistry = fleetRegistry;
        this.tenantAccess = tenantAccess;
    }

    @GetMapping
    @PreAuthorize("@tenantAccess.ownsProvider(#providerId)")
    public List<DriverResource> getByProvider(@RequestParam Long providerId) {
        return fleetCatalog.listDrivers(providerId).stream().map(DriversController::toResource).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DriverResource> getById(@PathVariable Long id) {
        return fleetCatalog.findDriver(id)
                .filter(driver -> tenantAccess.ownsProvider(driver.providerId()))
                .map(DriversController::toResource)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("@tenantAccess.ownsProvider(#resource.providerId())")
    public ResponseEntity<DriverResource> create(@RequestBody DriverResource resource) {
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(
                resource.providerId(), null, resource.firstName(), resource.lastName(),
                resource.licenseNumber(), resource.phoneNumber(), resource.email(),
                defaultStatus(resource.status())));
        return result.toOptional()
                .map(DriversController::toResource)
                .map(created -> new ResponseEntity<>(created, HttpStatus.CREATED))
                .orElse(ResponseEntity.badRequest().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DriverResource> update(@PathVariable Long id, @RequestBody DriverResource resource) {
        var existing = fleetCatalog.findDriver(id);
        if (existing.isEmpty() || !tenantAccess.ownsProvider(existing.get().providerId())) {
            return ResponseEntity.notFound().build();
        }
        Long providerId = resource.providerId() != null ? resource.providerId() : existing.get().providerId();
        if (!tenantAccess.ownsProvider(providerId)) return ResponseEntity.notFound().build();
        var result = fleetRegistry.updateDriver(new UpdateDriverCommand(id, resource.firstName(),
                resource.lastName(), resource.licenseNumber(), resource.phoneNumber(), resource.email(),
                defaultStatus(resource.status())));
        return result.toOptional()
                .map(DriversController::toResource)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var existing = fleetCatalog.findDriver(id);
        if (existing.isEmpty() || !tenantAccess.ownsProvider(existing.get().providerId())) {
            return ResponseEntity.notFound().build();
        }
        fleetRegistry.deactivateDriver(id);
        return ResponseEntity.noContent().build();
    }

    private static String defaultStatus(String status) {
        return status == null || status.isBlank() ? "AVAILABLE" : status;
    }

    private static DriverResource toResource(FleetCatalog.DriverSnapshot driver) {
        return new DriverResource(driver.id(), driver.providerId(), driver.firstName(), driver.lastName(),
                driver.licenseNumber(), driver.phoneNumber(), driver.email(), driver.status());
    }
}
