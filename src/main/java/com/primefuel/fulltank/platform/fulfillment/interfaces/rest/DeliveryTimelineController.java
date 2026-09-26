package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryStateTransitionRepository;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DeliveryBusinessJournalRepository;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.DeliveryTimelineItemResource;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.tracking.api.DeliveryTrackingQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reconstructs the delivery's business timeline from append-only sources plus current GPS load samples. */
@RestController
@RequestMapping("/api/v2/deliveries")
@Tag(name = "Delivery timeline", description = "Reconstructed physical and safety history")
public class DeliveryTimelineController {
    private final DeliveryTrackingLookup deliveries;
    private final FleetCatalog fleet;
    private final TenantAccess tenants;
    private final MembershipAccess membership;
    private final DeliveryStateTransitionRepository transitions;
    private final DeliveryBusinessJournalRepository journal;
    private final DeliveryTrackingQuery tracking;

    public DeliveryTimelineController(DeliveryTrackingLookup deliveries, FleetCatalog fleet, TenantAccess tenants,
            MembershipAccess membership, DeliveryStateTransitionRepository transitions,
            DeliveryBusinessJournalRepository journal, DeliveryTrackingQuery tracking) {
        this.deliveries = deliveries; this.fleet = fleet; this.tenants = tenants; this.membership = membership;
        this.transitions = transitions; this.journal = journal; this.tracking = tracking;
    }

    /** Returns the reconstructed timeline to the owning provider or the assigned driver. */
    @Operation(summary = "Get a delivery timeline", description = "Combines state transitions, safety decisions, valve facts, and retained load milestones; deleted GPS samples are absent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timeline returned in occurredAt/type/id order."),
            @ApiResponse(responseCode = "403", description = "Caller is outside the provider tenant and is not its assigned driver."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist.")
    })
    @GetMapping("/{deliveryId}/timeline")
    public ResponseEntity<List<DeliveryTimelineItemResource>> get(@PathVariable Long deliveryId) {
        var assignment = deliveries.findAssignedDelivery(deliveryId);
        if (assignment.isEmpty()) return ResponseEntity.notFound().build();
        boolean allowed = tenants.ownsProvider(assignment.get().providerId());
        if (!allowed) {
            var userId = membership.currentUserId();
            var driver = fleet.findDriver(assignment.get().driverId());
            if (driver.isEmpty()) return ResponseEntity.notFound().build();
            if (!assignment.get().providerId().equals(driver.get().providerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            allowed = userId.isPresent() && userId.get().equals(driver.get().userId());
        }
        if (!allowed) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        var items = new ArrayList<DeliveryTimelineItemResource>();
        var stateTransitions = transitions.findByDeliveryId(deliveryId);
        stateTransitions.forEach(t -> items.add(new DeliveryTimelineItemResource(t.occurredAt(), "STATE_TRANSITION",
                (t.fromState() == null ? "LEGACY" : t.fromState().name()) + " → " + t.toState().name(), String.valueOf(t.id()))));
        if (stateTransitions.stream().noneMatch(t -> "ASSIGNED".equals(t.toState().name()))) {
            items.add(new DeliveryTimelineItemResource(stateTransitions.isEmpty()
                    ? java.time.Instant.EPOCH : stateTransitions.getFirst().occurredAt(), "LEGACY_GAP",
                    "Initial state transition is missing", "legacy-gap-" + deliveryId));
        }
        journal.findByDeliveryIdOrderByOccurredAtAsc(deliveryId).forEach(j -> items.add(
                new DeliveryTimelineItemResource(j.getOccurredAt(), j.getType(), j.getSummary(), j.getRefId())));
        tracking.samples(deliveryId).stream().filter(s -> "LOAD".equals(s.kind())).forEach(s -> items.add(
                new DeliveryTimelineItemResource(s.recordedAt(), "LOAD_MILESTONE", s.milestone(), String.valueOf(s.evidenceId()))));
        items.sort(Comparator.comparing(DeliveryTimelineItemResource::occurredAt)
                .thenComparing(DeliveryTimelineItemResource::type)
                .thenComparing(DeliveryTimelineItemResource::refId, DeliveryTimelineController::compareIds));
        return ResponseEntity.ok(List.copyOf(items));
    }

    private static int compareIds(String left, String right) {
        try { return Long.compare(Long.parseLong(left), Long.parseLong(right)); }
        catch (NumberFormatException ignored) { return left.compareTo(right); }
    }
}
