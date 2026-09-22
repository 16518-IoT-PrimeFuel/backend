package com.primefuel.fulltank.platform.fleet.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.EligibilityQuery;
import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateTankerCommand;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.EligibilityResource;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.TankerInputResource;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.TankerV2Resource;
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
 * v2 tanker catalog (S12/T12-A). Same tenancy and lifecycle rules as drivers: the tenant comes from the
 * principal, and disabling preserves the row.
 */
@RestController
@RequestMapping(value = "/api/v2/tankers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Fleet tankers", description = "Tanker catalog and lifecycle (v2)")
public class TankersV2Controller {

    private final FleetCatalog fleetCatalog;
    private final FleetRegistry fleetRegistry;
    private final EligibilityQuery eligibilityQuery;
    private final TenantAccess tenantAccess;

    public TankersV2Controller(FleetCatalog fleetCatalog,
                               FleetRegistry fleetRegistry,
                               EligibilityQuery eligibilityQuery,
                               TenantAccess tenantAccess) {
        this.fleetCatalog = fleetCatalog;
        this.fleetRegistry = fleetRegistry;
        this.eligibilityQuery = eligibilityQuery;
        this.tenantAccess = tenantAccess;
    }

    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody TankerInputResource resource) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(
                providerId.get(), resource.licensePlate(), resource.brand(), resource.model(),
                resource.capacity(), resource.unit(), resource.status()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, TankersV2Controller::toResource, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TankerV2Resource>> list() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(fleetCatalog.listTankers(providerId.get()).stream()
                .map(TankersV2Controller::toResource).toList(), HttpStatus.OK);
    }

    @GetMapping("/{tankerId}")
    public ResponseEntity<TankerV2Resource> get(@PathVariable Long tankerId) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return fleetCatalog.findTanker(tankerId)
                .filter(tanker -> providerId.get().equals(tanker.providerId()))
                .map(tanker -> new ResponseEntity<>(toResource(tanker), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{tankerId}")
    public ResponseEntity<?> update(@PathVariable Long tankerId,
                                    @Valid @RequestBody TankerInputResource resource) {
        if (!owns(tankerId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var result = fleetRegistry.updateTanker(new UpdateTankerCommand(
                tankerId, resource.licensePlate(), resource.brand(), resource.model(),
                resource.capacity(), resource.unit(), resource.status()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, TankersV2Controller::toResource, HttpStatus.OK);
    }

    @PostMapping("/{tankerId}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable Long tankerId) {
        if (!owns(tankerId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.deactivateTanker(tankerId), TankersV2Controller::toResource, HttpStatus.OK);
    }

    @PostMapping("/{tankerId}/activate")
    public ResponseEntity<?> activate(@PathVariable Long tankerId) {
        if (!owns(tankerId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.activateTanker(tankerId), TankersV2Controller::toResource, HttpStatus.OK);
    }

    /** Only the tankers this tenant may actually be suggested (U07: available + active + same tenant). */
    @GetMapping("/eligible")
    public ResponseEntity<List<TankerV2Resource>> listEligible() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(eligibilityQuery.eligibleTankers(providerId.get()).stream()
                .map(TankersV2Controller::toResource).toList(), HttpStatus.OK);
    }

    @GetMapping("/{tankerId}/eligibility")
    public ResponseEntity<EligibilityResource> eligibility(@PathVariable Long tankerId) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return eligibilityQuery.assessTanker(providerId.get(), tankerId)
                .map(assessment -> new ResponseEntity<>(
                        new EligibilityResource(assessment.outcome().name(), assessment.reason()), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    private boolean owns(Long tankerId) {
        var providerId = tenantAccess.currentProviderId();
        return providerId.isPresent()
                && fleetCatalog.findTanker(tankerId)
                .map(tanker -> providerId.get().equals(tanker.providerId()))
                .orElse(false);
    }

    private static TankerV2Resource toResource(FleetCatalog.TankerSnapshot tanker) {
        return new TankerV2Resource(tanker.id(), tanker.providerId(), tanker.licensePlate(), tanker.brand(),
                tanker.model(), tanker.capacity(), tanker.unit(), tanker.status(), tanker.active());
    }
}
