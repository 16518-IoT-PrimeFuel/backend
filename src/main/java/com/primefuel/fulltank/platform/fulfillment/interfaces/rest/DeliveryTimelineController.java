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

/** Reconstruye la cronología de negocio de una entrega con sus fuentes históricas y las muestras retenidas. */
@RestController
@RequestMapping("/api/deliveries")
@Tag(name = "Cronología de entregas", description = "Historial físico y de seguridad reconstruido")
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

    /** Devuelve la cronología al distribuidor propietario o al conductor asignado. */
    @Operation(summary = "Consultar cronología de entrega", description = "Combina cambios de estado, decisiones de seguridad, eventos de válvula y registros de carga retenidos; las muestras GPS eliminadas no se incluyen.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cronología devuelta, ordenada por instante, tipo e identificador."),
            @ApiResponse(responseCode = "403", description = "El usuario no pertenece al distribuidor ni es el conductor asignado."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe.")
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
