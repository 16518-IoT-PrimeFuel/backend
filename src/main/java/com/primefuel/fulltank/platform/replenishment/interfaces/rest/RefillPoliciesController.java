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
