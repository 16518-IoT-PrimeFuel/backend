package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateTankerCommand;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.VehicleResource;
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

    /**
     * Lists the vehicles (tankers) of a provider tenant.
     *
     * <p>The requested provider must be the caller's own provider tenant.</p>
     */
    @Operation(summary = "List vehicles by provider",
            description = "Returns the tankers of the given provider tenant when it matches the caller's own provider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicles returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the requested provider tenant.")
    })
    @GetMapping
    @PreAuthorize("@tenantAccess.ownsProvider(#providerId)")
    public List<VehicleResource> getByProvider(@RequestParam Long providerId) {
        return fleetCatalog.listTankers(providerId).stream().map(VehiclesController::toResource).toList();
    }

    /**
     * Retrieves a single vehicle (tanker).
     *
     * <p>A vehicle of another provider tenant is reported as not found.</p>
     */
    @Operation(summary = "Get a vehicle by id",
            description = "Returns the tanker identified by the path id when it belongs to the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle returned."),
            @ApiResponse(responseCode = "404", description = "Vehicle does not exist or belongs to another provider tenant.")
    })
    @GetMapping("/{id}")
    public ResponseEntity<VehicleResource> getById(@PathVariable Long id) {
        return fleetCatalog.findTanker(id)
                .filter(tanker -> tenantAccess.ownsProvider(tanker.providerId()))
                .map(VehiclesController::toResource)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Registers a vehicle (tanker) for the caller's provider tenant.
     *
     * <p>The provider in the body must be the caller's own; a rejected registration is answered as a bad
     * request.</p>
     */
    @Operation(summary = "Create a vehicle",
            description = "Registers a tanker under the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vehicle created."),
            @ApiResponse(responseCode = "400", description = "The vehicle data is invalid."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the provider in the request body.")
    })
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

    /**
     * Updates a vehicle (tanker).
     *
     * <p>Only the owning provider tenant may update it; a vehicle that is missing, foreign, or that the
     * body tries to move to a different provider is reported as not found, and invalid data as a bad
     * request.</p>
     */
    @Operation(summary = "Update a vehicle",
            description = "Applies field changes to a tanker owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle updated."),
            @ApiResponse(responseCode = "400", description = "The vehicle data is invalid."),
            @ApiResponse(responseCode = "404", description = "Vehicle does not exist or is not owned by the caller.")
    })
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

    /**
     * Soft-disables a vehicle (tanker).
     *
     * <p>Only the owning provider tenant may disable it. The row is retained (no hard delete); only the
     * lifecycle flag changes.</p>
     */
    @Operation(summary = "Disable a vehicle",
            description = "Soft-disables a tanker owned by the caller's provider tenant, preserving the record.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vehicle disabled; no content returned."),
            @ApiResponse(responseCode = "404", description = "Vehicle does not exist or is not owned by the caller.")
    })
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
