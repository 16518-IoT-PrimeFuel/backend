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
