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

    /**
     * Registers a tanker in the caller's provider tenant.
     *
     * <p>The provider always comes from the principal, never the body.</p>
     */
    @Operation(summary = "Register a tanker",
            description = "Creates a tanker in the caller's provider tenant; the tenant is taken from the principal.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tanker created."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation or the tanker data is invalid."),
            @ApiResponse(responseCode = "403", description = "Caller has no provider identity.")
    })
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

    /**
     * Lists the tankers of the caller's provider tenant.
     *
     * <p>Always tenant-scoped through the principal.</p>
     */
    @Operation(summary = "List tankers",
            description = "Returns all tankers of the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tankers returned."),
            @ApiResponse(responseCode = "403", description = "Caller has no provider identity.")
    })
    @GetMapping
    public ResponseEntity<List<TankerV2Resource>> list() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(fleetCatalog.listTankers(providerId.get()).stream()
                .map(TankersV2Controller::toResource).toList(), HttpStatus.OK);
    }

    /**
     * Retrieves a single tanker.
     *
     * <p>Tenant-scoped through the principal; a tanker of another tenant is reported as not found.</p>
     */
    @Operation(summary = "Get a tanker by id",
            description = "Returns the tanker identified by the path id when it belongs to the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tanker returned."),
            @ApiResponse(responseCode = "403", description = "Caller has no provider identity."),
            @ApiResponse(responseCode = "404", description = "Tanker does not exist or belongs to another provider tenant.")
    })
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

    /**
     * Updates a tanker.
     *
     * <p>Only the owning provider tenant may update it; a foreign or missing tanker is reported as not
     * found and invalid data as a bad request.</p>
     */
    @Operation(summary = "Update a tanker",
            description = "Applies field changes to a tanker owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tanker updated."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation or the tanker data is invalid."),
            @ApiResponse(responseCode = "404", description = "Tanker does not exist or is not owned by the caller.")
    })
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

    /**
     * Disables a tanker.
     *
     * <p>Only the owning provider tenant may disable it. Disabling never deletes the row; it flips the
     * lifecycle flag and publishes a resource-disabled event.</p>
     */
    @Operation(summary = "Disable a tanker",
            description = "Soft-disables a tanker owned by the caller's provider tenant, preserving the record.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tanker disabled."),
            @ApiResponse(responseCode = "404", description = "Tanker does not exist or is not owned by the caller.")
    })
    @PostMapping("/{tankerId}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable Long tankerId) {
        if (!owns(tankerId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.deactivateTanker(tankerId), TankersV2Controller::toResource, HttpStatus.OK);
    }

    /**
     * Enables a previously disabled tanker.
     *
     * <p>Only the owning provider tenant may enable it; this publishes a resource-enabled event.</p>
     */
    @Operation(summary = "Enable a tanker",
            description = "Re-enables a disabled tanker owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tanker enabled."),
            @ApiResponse(responseCode = "404", description = "Tanker does not exist or is not owned by the caller.")
    })
    @PostMapping("/{tankerId}/activate")
    public ResponseEntity<?> activate(@PathVariable Long tankerId) {
        if (!owns(tankerId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.activateTanker(tankerId), TankersV2Controller::toResource, HttpStatus.OK);
    }

    /**
     * Lists the tankers that may actually be suggested for a delivery.
     *
     * <p>U07: eligible = allowed status + active + same tenant. Tankers that are busy are excluded from
     * this suggestion list.</p>
     */
    @Operation(summary = "List eligible tankers",
            description = "Returns the tankers of the caller's provider tenant that are currently eligible to be suggested.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eligible tankers returned."),
            @ApiResponse(responseCode = "403", description = "Caller has no provider identity.")
    })
    @GetMapping("/eligible")
    public ResponseEntity<List<TankerV2Resource>> listEligible() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(eligibilityQuery.eligibleTankers(providerId.get()).stream()
                .map(TankersV2Controller::toResource).toList(), HttpStatus.OK);
    }

    /**
     * Assesses the eligibility of a single tanker.
     *
     * <p>Returns a three-valued outcome (eligible / busy / ineligible) with a reason. Tenant-scoped
     * through the principal; a foreign or missing tanker is reported as not found.</p>
     */
    @Operation(summary = "Assess tanker eligibility",
            description = "Returns the eligibility outcome and reason for a tanker owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eligibility assessment returned."),
            @ApiResponse(responseCode = "403", description = "Caller has no provider identity."),
            @ApiResponse(responseCode = "404", description = "Tanker does not exist or belongs to another provider tenant.")
    })
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
