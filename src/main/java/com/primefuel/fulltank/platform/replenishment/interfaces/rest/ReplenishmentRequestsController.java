package com.primefuel.fulltank.platform.replenishment.interfaces.rest;

import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.application.queryservices.ReplenishmentQueryService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CancelReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.RejectReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetReplenishmentRequestByIdQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetReplenishmentRequestsByOrganizationQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.CreateReplenishmentRequestResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.RejectReplenishmentRequestResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.ReplenishmentRequestResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.transform.ReplenishmentRequestResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v2/replenishment-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Replenishment", description = "Replenishment request lifecycle (v2)")
public class ReplenishmentRequestsController {

    private final ReplenishmentCommandService commandService;
    private final ReplenishmentQueryService queryService;
    private final MembershipAccess membershipAccess;
    private final TenantAccess tenantAccess;

    public ReplenishmentRequestsController(ReplenishmentCommandService commandService,
                                           ReplenishmentQueryService queryService,
                                           MembershipAccess membershipAccess,
                                           TenantAccess tenantAccess) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.membershipAccess = membershipAccess;
        this.tenantAccess = tenantAccess;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateReplenishmentRequestResource resource) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var source = resource.source() == null
                ? ReplenishmentSource.MANUAL
                : ReplenishmentSource.valueOf(resource.source().trim().toUpperCase());
        var result = commandService.handle(new CreateReplenishmentRequestCommand(
                organizationId.get(), resource.customerAccountId(), resource.tankId(), resource.providerId(),
                resource.fuelProductId(), resource.quantity(), resource.unit(), source, resource.episodeKey()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ReplenishmentRequestResource>> list() {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var requests = queryService.handle(new GetReplenishmentRequestsByOrganizationQuery(organizationId.get()));
        return new ResponseEntity<>(requests.stream()
                .map(ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain).toList(), HttpStatus.OK);
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ReplenishmentRequestResource> get(@PathVariable Long requestId) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return queryService.handle(new GetReplenishmentRequestByIdQuery(requestId))
                .filter(request -> organizationId.get().equals(request.getOrganizationId()))
                .map(request -> new ResponseEntity<>(
                        ReplenishmentRequestResourceFromDomainAssembler.toResourceFromDomain(request), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/{requestId}/accept")
    public ResponseEntity<?> accept(@PathVariable Long requestId) {
        if (!providerOwns(requestId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = commandService.handle(new AcceptReplenishmentRequestCommand(requestId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long requestId,
                                    @Valid @RequestBody RejectReplenishmentRequestResource resource) {
        if (!providerOwns(requestId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = commandService.handle(new RejectReplenishmentRequestCommand(requestId, resource.reason()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long requestId) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty() || !organizationIdOwns(requestId, organizationId.get())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = commandService.handle(new CancelReplenishmentRequestCommand(requestId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }

    private boolean providerOwns(Long requestId) {
        var providerId = tenantAccess.currentProviderId();
        return providerId.isPresent()
                && queryService.handle(new GetReplenishmentRequestByIdQuery(requestId))
                .map(request -> providerId.get().equals(request.getProviderId()))
                .orElse(false);
    }

    private boolean organizationIdOwns(Long requestId, Long organizationId) {
        return queryService.handle(new GetReplenishmentRequestByIdQuery(requestId))
                .map(request -> organizationId.equals(request.getOrganizationId()))
                .orElse(false);
    }
}
