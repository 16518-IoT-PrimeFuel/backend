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

    @GetMapping
    public ResponseEntity<List<DriverV2Resource>> list() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(fleetCatalog.listDrivers(providerId.get()).stream()
                .map(DriversV2Controller::toResource).toList(), HttpStatus.OK);
    }

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

    @PostMapping("/{driverId}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable Long driverId) {
        if (!owns(driverId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.deactivateDriver(driverId), DriversV2Controller::toResource, HttpStatus.OK);
    }

    @PostMapping("/{driverId}/activate")
    public ResponseEntity<?> activate(@PathVariable Long driverId) {
        if (!owns(driverId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.activateDriver(driverId), DriversV2Controller::toResource, HttpStatus.OK);
    }

    /** Only the drivers this tenant may actually be suggested (U07: available + active + same tenant). */
    @GetMapping("/eligible")
    public ResponseEntity<List<DriverV2Resource>> listEligible() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(eligibilityQuery.eligibleDrivers(providerId.get()).stream()
                .map(DriversV2Controller::toResource).toList(), HttpStatus.OK);
    }

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
