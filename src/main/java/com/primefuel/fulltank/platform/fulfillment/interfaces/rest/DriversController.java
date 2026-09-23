package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateDriverCommand;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.DriverResource;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    /**
     * Lists the drivers of a provider tenant.
     *
     * <p>The requested provider must be the caller's own provider tenant.</p>
     */
    @Operation(summary = "List drivers by provider",
            description = "Returns the drivers of the given provider tenant when it matches the caller's own provider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Drivers returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the requested provider tenant.")
    })
    @GetMapping
    @PreAuthorize("@tenantAccess.ownsProvider(#providerId)")
    public List<DriverResource> getByProvider(@RequestParam Long providerId) {
        return fleetCatalog.listDrivers(providerId).stream().map(DriversController::toResource).toList();
    }

    /**
     * Retrieves a single driver.
     *
     * <p>A driver of another provider tenant is reported as not found.</p>
     */
    @Operation(summary = "Get a driver by id",
            description = "Returns the driver identified by the path id when it belongs to the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver returned."),
            @ApiResponse(responseCode = "404", description = "Driver does not exist or belongs to another provider tenant.")
    })
    @GetMapping("/{id}")
    public ResponseEntity<DriverResource> getById(@PathVariable Long id) {
        return fleetCatalog.findDriver(id)
                .filter(driver -> tenantAccess.ownsProvider(driver.providerId()))
                .map(DriversController::toResource)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Registers a driver for the caller's provider tenant.
     *
     * <p>The provider in the body must be the caller's own; a rejected registration (invalid driver data)
     * is answered as a bad request.</p>
     */
    @Operation(summary = "Create a driver",
            description = "Registers a driver under the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Driver created."),
            @ApiResponse(responseCode = "400", description = "The driver data is invalid."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the provider in the request body.")
    })
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

    /**
     * Updates a driver.
     *
     * <p>Only the owning provider tenant may update it; a driver that is missing, foreign, or that the
     * body tries to move to a different provider is reported as not found, and invalid data as a bad
     * request.</p>
     */
    @Operation(summary = "Update a driver",
            description = "Applies field changes to a driver owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver updated."),
            @ApiResponse(responseCode = "400", description = "The driver data is invalid."),
            @ApiResponse(responseCode = "404", description = "Driver does not exist or is not owned by the caller.")
    })
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

    /**
     * Soft-disables a driver.
     *
     * <p>Only the owning provider tenant may disable it. The row is retained (no hard delete); only the
     * lifecycle flag changes.</p>
     */
    @Operation(summary = "Disable a driver",
            description = "Soft-disables a driver owned by the caller's provider tenant, preserving the record.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Driver disabled; no content returned."),
            @ApiResponse(responseCode = "404", description = "Driver does not exist or is not owned by the caller.")
    })
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
