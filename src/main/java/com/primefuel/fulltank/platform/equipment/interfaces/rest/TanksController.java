package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.application.queryservices.TankQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetTanksByOrganizationQuery;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.RegisterTankResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.TankResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.transform.TankResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
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

@RestController
@RequestMapping(value = "/api/v2/tanks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tanks", description = "Tank assets and configuration (v2)")
public class TanksController {

    private final TankCommandService tankCommandService;
    private final TankQueryService tankQueryService;
    private final MembershipAccess membershipAccess;

    public TanksController(TankCommandService tankCommandService,
                           TankQueryService tankQueryService,
                           MembershipAccess membershipAccess) {
        this.tankCommandService = tankCommandService;
        this.tankQueryService = tankQueryService;
        this.membershipAccess = membershipAccess;
    }

    /**
     * Registers a tank for a customer of the caller's organization.
     *
     * <p>The organization comes from the principal. The customer must belong to that organization and,
     * when a site is given, the site must belong to the same customer and organization. A tank whose
     * capacity/level invariants are violated is rejected as a validation error.</p>
     */
    @Operation(summary = "Register a tank",
            description = "Creates a tank under a customer of the caller's organization, validating customer, site and capacity invariants.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tank created."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, no organization was resolved, or the customer/site/capacity invariants are violated."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated or has no active organization."),
            @ApiResponse(responseCode = "409", description = "The referenced legacy equipment is already mapped to a tank.")
    })
    @PostMapping
    public ResponseEntity<?> registerTank(@Valid @RequestBody RegisterTankResource resource) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = tankCommandService.handle(new RegisterTankCommand(
                organizationId.get(), resource.customerAccountId(), resource.siteId(), resource.name(),
                resource.fuelType(), resource.capacity(), resource.unit(), resource.initialLevel(), null));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, TankResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.CREATED);
    }

    /**
     * Lists the tanks of the caller's organization.
     *
     * <p>Scoped to the organization derived from the principal.</p>
     */
    @Operation(summary = "List tanks",
            description = "Returns the tanks belonging to the caller's organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tanks returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated or has no active organization.")
    })
    @GetMapping
    public ResponseEntity<List<TankResource>> listTanks() {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var tanks = tankQueryService.handle(new GetTanksByOrganizationQuery(organizationId.get()));
        return new ResponseEntity<>(
                tanks.stream().map(TankResourceFromDomainAssembler::toResourceFromDomain).toList(),
                HttpStatus.OK);
    }

    /**
     * Retrieves a single tank.
     *
     * <p>The tank must belong to the caller's organization; a tank of another tenant is reported as
     * not found.</p>
     */
    @Operation(summary = "Get a tank by id",
            description = "Returns the tank identified by the path id when it belongs to the caller's organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tank returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated or has no active organization."),
            @ApiResponse(responseCode = "404", description = "Tank does not exist or belongs to another organization.")
    })
    @GetMapping("/{tankId}")
    public ResponseEntity<TankResource> getTank(@PathVariable Long tankId) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return tankQueryService.handle(new com.primefuel.fulltank.platform.equipment.domain.model.queries.GetTankByIdQuery(tankId))
                .filter(tank -> organizationId.get().equals(tank.getOrganizationId()))
                .map(tank -> new ResponseEntity<>(
                        TankResourceFromDomainAssembler.toResourceFromDomain(tank), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
