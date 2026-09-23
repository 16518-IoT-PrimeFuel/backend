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

    /**
     * Creates a replenishment request for the caller's organization.
     *
     * <p>The organization comes from the principal. The referenced fuel product must be visible to the
     * given provider tenant, and when an {@code episodeKey} is supplied the call is idempotent: a
     * request already created for that episode is returned instead of a duplicate.</p>
     */
    @Operation(summary = "Create a replenishment request",
            description = "Creates a replenishment request owned by the caller's organization; creation is idempotent per episodeKey.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Replenishment request created (or the existing episode request returned)."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, no organization was resolved, or a field value (e.g. source) is invalid."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated or has no active organization."),
            @ApiResponse(responseCode = "404", description = "The referenced fuel product is not available for the given provider.")
    })
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

    /**
     * Lists the replenishment requests of the caller's organization.
     *
     * <p>Scoped to the organization resolved from the principal.</p>
     */
    @Operation(summary = "List replenishment requests",
            description = "Returns the replenishment requests belonging to the caller's organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replenishment requests returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated or has no active organization.")
    })
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

    /**
     * Retrieves a single replenishment request.
     *
     * <p>The request must belong to the caller's organization; anything else is reported as not
     * found.</p>
     */
    @Operation(summary = "Get a replenishment request by id",
            description = "Returns the replenishment request identified by the path id when it belongs to the caller's organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replenishment request returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated or has no active organization."),
            @ApiResponse(responseCode = "404", description = "Replenishment request does not exist or belongs to another organization.")
    })
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

    /**
     * Accepts a pending replenishment request.
     *
     * <p>Only the provider tenant the request was addressed to may accept it. The request must still
     * be pending; an already-decided request (or a concurrent decision) is reported as a conflict.</p>
     */
    @Operation(summary = "Accept a replenishment request",
            description = "Accepts a pending replenishment request on behalf of the addressed provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replenishment request accepted."),
            @ApiResponse(responseCode = "400", description = "The request could not be accepted because its state or arguments are invalid."),
            @ApiResponse(responseCode = "403", description = "Caller is not the addressed provider tenant, or the request does not exist."),
            @ApiResponse(responseCode = "404", description = "Replenishment request does not exist."),
            @ApiResponse(responseCode = "409", description = "The request is not pending or was decided concurrently.")
    })
    @PostMapping("/{requestId}/accept")
    public ResponseEntity<?> accept(@PathVariable Long requestId) {
        if (!providerOwns(requestId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = commandService.handle(new AcceptReplenishmentRequestCommand(requestId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }

    /**
     * Rejects a pending replenishment request.
     *
     * <p>Only the addressed provider tenant may reject it; the reason is required and the request must
     * still be pending.</p>
     */
    @Operation(summary = "Reject a replenishment request",
            description = "Rejects a pending replenishment request on behalf of the addressed provider tenant, recording a reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replenishment request rejected."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation or the request could not be rejected."),
            @ApiResponse(responseCode = "403", description = "Caller is not the addressed provider tenant, or the request does not exist."),
            @ApiResponse(responseCode = "404", description = "Replenishment request does not exist."),
            @ApiResponse(responseCode = "409", description = "The request is not pending or was decided concurrently.")
    })
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

    /**
     * Cancels a replenishment request.
     *
     * <p>Only the organization that owns the request may cancel it, and only while it is pending.</p>
     */
    @Operation(summary = "Cancel a replenishment request",
            description = "Cancels a pending replenishment request on behalf of the owning organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replenishment request cancelled."),
            @ApiResponse(responseCode = "403", description = "Caller has no active organization, does not own the request, or the request does not exist."),
            @ApiResponse(responseCode = "404", description = "Replenishment request does not exist."),
            @ApiResponse(responseCode = "409", description = "The request is not pending or was decided concurrently.")
    })
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
