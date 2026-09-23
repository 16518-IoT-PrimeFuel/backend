package com.primefuel.fulltank.platform.fleet.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.EligibilityQuery;
import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateDriverCommand;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.DriverInputResource;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.DriverV2Resource;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.EligibilityResource;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * v2 driver catalog (S12/T12-A). The provider is always the principal's tenant; a resource the principal
 * does not own is simply not found. Enabling/disabling is explicit — there is no hard delete.
 */
@RestController
@RequestMapping(value = "/api/v2/drivers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Fleet drivers", description = "Driver catalog and lifecycle (v2)")
public class DriversV2Controller {

    private final FleetCatalog fleetCatalog;
    private final FleetRegistry fleetRegistry;
    private final EligibilityQuery eligibilityQuery;
    private final TenantAccess tenantAccess;

    public DriversV2Controller(FleetCatalog fleetCatalog,
                               FleetRegistry fleetRegistry,
                               EligibilityQuery eligibilityQuery,
                               TenantAccess tenantAccess) {
        this.fleetCatalog = fleetCatalog;
        this.fleetRegistry = fleetRegistry;
        this.eligibilityQuery = eligibilityQuery;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Registers a driver in the caller's provider tenant.
     *
     * <p>The provider always comes from the principal, never the body, so a caller cannot create drivers
     * for another tenant.</p>
     */
    @Operation(summary = "Register a driver",
            description = "Creates a driver in the caller's provider tenant; the tenant is taken from the principal.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Driver created."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation or the driver data is invalid."),
            @ApiResponse(responseCode = "403", description = "Caller has no provider identity.")
    })
    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody DriverInputResource resource) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(
                providerId.get(), resource.userId(), resource.firstName(), resource.lastName(),
                resource.licenseNumber(), resource.phoneNumber(), resource.email(), resource.status()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, DriversV2Controller::toResource, HttpStatus.CREATED);
    }

    /**
     * Lists the drivers of the caller's provider tenant.
     *
     * <p>Always tenant-scoped through the principal.</p>
     */
    @Operation(summary = "List drivers",
            description = "Returns all drivers of the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Drivers returned."),
            @ApiResponse(responseCode = "403", description = "Caller has no provider identity.")
    })
    @GetMapping
    public ResponseEntity<List<DriverV2Resource>> list() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(fleetCatalog.listDrivers(providerId.get()).stream()
                .map(DriversV2Controller::toResource).toList(), HttpStatus.OK);
    }

    /**
     * Retrieves a single driver.
     *
     * <p>Tenant-scoped through the principal; a driver of another tenant is reported as not found.</p>
     */
    @Operation(summary = "Get a driver by id",
            description = "Returns the driver identified by the path id when it belongs to the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver returned."),
            @ApiResponse(responseCode = "403", description = "Caller has no provider identity."),
            @ApiResponse(responseCode = "404", description = "Driver does not exist or belongs to another provider tenant.")
    })
    @GetMapping("/{driverId}")
    public ResponseEntity<DriverV2Resource> get(@PathVariable Long driverId) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return fleetCatalog.findDriver(driverId)
                .filter(driver -> providerId.get().equals(driver.providerId()))
                .map(driver -> new ResponseEntity<>(toResource(driver), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Updates a driver.
     *
     * <p>Only the owning provider tenant may update it; a foreign or missing driver is reported as not
     * found and invalid data as a bad request.</p>
     */
    @Operation(summary = "Update a driver",
            description = "Applies field changes to a driver owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver updated."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation or the driver data is invalid."),
            @ApiResponse(responseCode = "404", description = "Driver does not exist or is not owned by the caller.")
    })
    @PutMapping("/{driverId}")
    public ResponseEntity<?> update(@PathVariable Long driverId,
                                    @Valid @RequestBody DriverInputResource resource) {
        if (!owns(driverId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var result = fleetRegistry.updateDriver(new UpdateDriverCommand(
                driverId, resource.firstName(), resource.lastName(), resource.licenseNumber(),
                resource.phoneNumber(), resource.email(), resource.status()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, DriversV2Controller::toResource, HttpStatus.OK);
    }

    /**
     * Disables a driver.
     *
     * <p>Only the owning provider tenant may disable it. Disabling never deletes the row; it flips the
     * lifecycle flag and publishes a resource-disabled event.</p>
     */
    @Operation(summary = "Disable a driver",
            description = "Soft-disables a driver owned by the caller's provider tenant, preserving the record.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver disabled."),
            @ApiResponse(responseCode = "404", description = "Driver does not exist or is not owned by the caller.")
    })
    @PostMapping("/{driverId}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable Long driverId) {
        if (!owns(driverId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.deactivateDriver(driverId), DriversV2Controller::toResource, HttpStatus.OK);
    }

    /**
     * Enables a previously disabled driver.
     *
     * <p>Only the owning provider tenant may enable it; this publishes a resource-enabled event.</p>
     */
    @Operation(summary = "Enable a driver",
            description = "Re-enables a disabled driver owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver enabled."),
            @ApiResponse(responseCode = "404", description = "Driver does not exist or is not owned by the caller.")
    })
    @PostMapping("/{driverId}/activate")
    public ResponseEntity<?> activate(@PathVariable Long driverId) {
        if (!owns(driverId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.activateDriver(driverId), DriversV2Controller::toResource, HttpStatus.OK);
    }

    /**
     * Lists the drivers that may actually be suggested for a delivery.
     *
     * <p>U07: eligible = allowed status + active + same tenant. Drivers that are busy are excluded from
     * this suggestion list.</p>
     */
    @Operation(summary = "List eligible drivers",
            description = "Returns the drivers of the caller's provider tenant that are currently eligible to be suggested.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eligible drivers returned."),
            @ApiResponse(responseCode = "403", description = "Caller has no provider identity.")
    })
    @GetMapping("/eligible")
    public ResponseEntity<List<DriverV2Resource>> listEligible() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(eligibilityQuery.eligibleDrivers(providerId.get()).stream()
                .map(DriversV2Controller::toResource).toList(), HttpStatus.OK);
    }

    /**
     * Assesses the eligibility of a single driver.
     *
     * <p>Returns a three-valued outcome (eligible / busy / ineligible) with a reason. Tenant-scoped
     * through the principal; a foreign or missing driver is reported as not found.</p>
     */
    @Operation(summary = "Assess driver eligibility",
            description = "Returns the eligibility outcome and reason for a driver owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eligibility assessment returned."),
            @ApiResponse(responseCode = "403", description = "Caller has no provider identity."),
            @ApiResponse(responseCode = "404", description = "Driver does not exist or belongs to another provider tenant.")
    })
    @GetMapping("/{driverId}/eligibility")
    public ResponseEntity<EligibilityResource> eligibility(@PathVariable Long driverId) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return eligibilityQuery.assessDriver(providerId.get(), driverId)
                .map(assessment -> new ResponseEntity<>(
                        new EligibilityResource(assessment.outcome().name(), assessment.reason()), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    private boolean owns(Long driverId) {
        var providerId = tenantAccess.currentProviderId();
        return providerId.isPresent()
                && fleetCatalog.findDriver(driverId)
                .map(driver -> providerId.get().equals(driver.providerId()))
                .orElse(false);
    }

    private static DriverV2Resource toResource(FleetCatalog.DriverSnapshot driver) {
        return new DriverV2Resource(driver.id(), driver.providerId(), driver.userId(), driver.firstName(),
                driver.lastName(), driver.licenseNumber(), driver.phoneNumber(), driver.email(),
                driver.status(), driver.active());
    }
}
