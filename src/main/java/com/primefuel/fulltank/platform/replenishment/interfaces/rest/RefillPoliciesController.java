package com.primefuel.fulltank.platform.replenishment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.RefillPolicyCommandService;
import com.primefuel.fulltank.platform.replenishment.application.queryservices.RefillPolicyQueryService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConfigureRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetRefillEpisodesByTankQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetRefillPolicyByTankQuery;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.ConfigureRefillPolicyResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.RefillEpisodeResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.RefillPolicyResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.transform.RefillEpisodeResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.transform.RefillPolicyResourceFromDomainAssembler;
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
 * Per-tank refill policy (S09) and the read side of the shadow decisions. Automation is opt-in through
 * {@code autoGenerateEnabled}; until a tank opts in, everything here is observability only.
 */
@RestController
@RequestMapping(value = "/api/v2/tanks/{tankId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Refill policy", description = "Low-level policy and refill episodes (v2)")
public class RefillPoliciesController {

    private final RefillPolicyCommandService commandService;
    private final RefillPolicyQueryService queryService;
    private final MembershipAccess membershipAccess;
    private final TankAssets tankAssets;

    public RefillPoliciesController(RefillPolicyCommandService commandService,
                                    RefillPolicyQueryService queryService,
                                    MembershipAccess membershipAccess,
                                    TankAssets tankAssets) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.membershipAccess = membershipAccess;
        this.tankAssets = tankAssets;
    }

    /**
     * Creates or reconfigures the refill policy of a tank.
     *
     * <p>The tank must belong to the caller's organization. All policy fields are optional and fall
     * back to the approved global defaults; an existing policy is reconfigured in place. The tank is
     * first checked through {@link TankAssets}, so a tank that is missing or foreign is refused as
     * forbidden rather than reported as not found.</p>
     */
    @Operation(summary = "Configure a tank refill policy",
            description = "Creates or updates the refill policy of a tank owned by the caller's organization, with optional overrides over the global defaults.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refill policy created or updated."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation or a policy value is invalid."),
            @ApiResponse(responseCode = "403", description = "Caller has no active organization, does not own the tank, or the policy belongs to another organization.")
    })
    @PutMapping("/refill-policy")
    public ResponseEntity<?> configure(@PathVariable Long tankId,
                                       @Valid @RequestBody ConfigureRefillPolicyResource resource) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty() || !ownsTank(tankId, organizationId.get())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = commandService.handle(new ConfigureRefillPolicyCommand(
                tankId, organizationId.get(), resource.lowLevelPercent(), resource.hysteresisPercent(),
                resource.targetLevelPercent(), resource.providerId(), resource.fuelProductId(),
                resource.autoGenerateEnabled()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, RefillPolicyResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }

    /**
     * Retrieves the refill policy of a tank.
     *
     * <p>The tank must belong to the caller's organization (otherwise forbidden); a tank without a
     * configured policy yet is reported as not found.</p>
     */
    @Operation(summary = "Get a tank refill policy",
            description = "Returns the current refill policy of a tank owned by the caller's organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refill policy returned."),
            @ApiResponse(responseCode = "403", description = "Caller has no active organization or does not own the tank."),
            @ApiResponse(responseCode = "404", description = "The tank has no refill policy configured.")
    })
    @GetMapping("/refill-policy")
    public ResponseEntity<RefillPolicyResource> get(@PathVariable Long tankId) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty() || !ownsTank(tankId, organizationId.get())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return queryService.handle(new GetRefillPolicyByTankQuery(tankId))
                .map(policy -> new ResponseEntity<>(
                        RefillPolicyResourceFromDomainAssembler.toResourceFromDomain(policy), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Lists the refill episodes recorded for a tank.
     *
     * <p>The tank must belong to the caller's organization; episodes are the shadow decisions taken by
     * the evaluator on that tank.</p>
     */
    @Operation(summary = "List refill episodes of a tank",
            description = "Returns the refill episodes recorded for a tank owned by the caller's organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refill episodes returned."),
            @ApiResponse(responseCode = "403", description = "Caller has no active organization or does not own the tank.")
    })
    @GetMapping("/refill-episodes")
    public ResponseEntity<List<RefillEpisodeResource>> episodes(@PathVariable Long tankId) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty() || !ownsTank(tankId, organizationId.get())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var episodes = queryService.handle(new GetRefillEpisodesByTankQuery(tankId));
        return new ResponseEntity<>(episodes.stream()
                .map(RefillEpisodeResourceFromDomainAssembler::toResourceFromDomain).toList(), HttpStatus.OK);
    }

    private boolean ownsTank(Long tankId, Long organizationId) {
        return tankAssets.findById(tankId)
                .map(snapshot -> organizationId.equals(snapshot.organizationId()))
                .orElse(false);
    }
}
