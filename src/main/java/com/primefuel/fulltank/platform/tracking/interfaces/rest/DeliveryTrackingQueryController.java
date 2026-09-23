package com.primefuel.fulltank.platform.tracking.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.tracking.api.DeliveryTrackingQuery;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.DeliveryTrackingResource;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.TrackingSampleResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 transport-tracking query (S16/T16-B). It exposes the delivery's latest trusted projection and its
 * chronological trail through the public {@link DeliveryTrackingQuery} seam.
 *
 * <p><strong>Authorization</strong> is resolved server-side from the delivery assignment and the principal
 * (never from the path beyond the delivery id): the delivery's <em>owning provider</em> or the <em>assigned
 * driver</em> may read the tracking. A caller from another tenant is rejected with 403 and a missing delivery
 * (or delivery without tracking) is a 404.
 */
@RestController
@RequestMapping(value = "/api/v2/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Transport tracking", description = "Transport tracking query (v2)")
public class DeliveryTrackingQueryController {

    private final DeliveryTrackingQuery trackingQuery;
    private final DeliveryTrackingLookup deliveryTrackingLookup;
    private final FleetCatalog fleetCatalog;
    private final TenantAccess tenantAccess;
    private final MembershipAccess membershipAccess;

    public DeliveryTrackingQueryController(DeliveryTrackingQuery trackingQuery,
                                           DeliveryTrackingLookup deliveryTrackingLookup,
                                           FleetCatalog fleetCatalog,
                                           TenantAccess tenantAccess,
                                           MembershipAccess membershipAccess) {
        this.trackingQuery = trackingQuery;
        this.deliveryTrackingLookup = deliveryTrackingLookup;
        this.fleetCatalog = fleetCatalog;
        this.tenantAccess = tenantAccess;
        this.membershipAccess = membershipAccess;
    }

    /**
     * Returns the latest trusted tracking of a delivery.
     *
     * <p>Visible to the owning provider or the assigned driver; a foreign tenant is rejected and a delivery
     * without tracking yet is reported as not found.</p>
     */
    @Operation(summary = "Get the latest tracking of a delivery",
            description = "Returns the latest trusted position and load state of a delivery visible to the owning provider or the assigned driver.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Latest tracking returned."),
            @ApiResponse(responseCode = "403", description = "The caller is neither the owning provider nor the assigned driver."),
            @ApiResponse(responseCode = "404", description = "The delivery does not exist or has no tracking yet.")
    })
    @GetMapping("/{deliveryId}/tracking")
    public ResponseEntity<?> latest(@PathVariable Long deliveryId) {
        var denial = denialOrNull(deliveryId);
        if (denial != null) {
            return denial;
        }
        return trackingQuery.latest(deliveryId)
                .map(snapshot -> new ResponseEntity<>(toResource(snapshot), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Returns the chronological trail of a delivery, ordered by the device clock.
     *
     * <p>Each sample keeps its reception instant and whether it advanced the latest, so out-of-order (jitter)
     * samples are visible in place. Visible to the owning provider or the assigned driver.</p>
     */
    @Operation(summary = "List the tracking trail of a delivery",
            description = "Returns the delivery's transport-evidence samples ordered by device timestamp, with reception instant and latestAdvanced visible.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timeline returned (possibly empty)."),
            @ApiResponse(responseCode = "403", description = "The caller is neither the owning provider nor the assigned driver."),
            @ApiResponse(responseCode = "404", description = "The delivery does not exist.")
    })
    @GetMapping("/{deliveryId}/tracking/samples")
    public ResponseEntity<?> samples(@PathVariable Long deliveryId) {
        var denial = denialOrNull(deliveryId);
        if (denial != null) {
            return denial;
        }
        var resources = trackingQuery.samples(deliveryId).stream()
                .map(snapshot -> new TrackingSampleResource(snapshot.evidenceId(), snapshot.kind(),
                        snapshot.latitude(), snapshot.longitude(), snapshot.accuracyMeters(),
                        snapshot.milestone(), snapshot.volume(), snapshot.unit(), snapshot.recordedAt(),
                        snapshot.receivedAt(), snapshot.latestAdvanced()))
                .toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Resolves whether the caller may read the delivery's tracking. Returns {@code null} when allowed, or the
     * denial response otherwise. The owning provider (any principal of that provider tenant) or the assigned
     * driver of a tenant-consistent assignment may read; everything else is forbidden, and a missing delivery
     * or driver is not found.
     */
    private ResponseEntity<?> denialOrNull(Long deliveryId) {
        var delivery = deliveryTrackingLookup.findAssignedDelivery(deliveryId);
        if (delivery.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var assignment = delivery.get();
        var driver = fleetCatalog.findDriver(assignment.driverId());
        if (driver.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var assignedDriver = driver.get();

        boolean providerOwner = tenantAccess.currentProviderId()
                .map(providerId -> providerId.equals(assignment.providerId()))
                .orElse(false);
        boolean tenantConsistent = assignment.providerId().equals(assignedDriver.providerId());
        boolean assignedDriverCaller = tenantConsistent
                && assignedDriver.userId() != null
                && membershipAccess.currentUserId()
                .map(userId -> userId.equals(assignedDriver.userId()))
                .orElse(false);

        if (providerOwner || assignedDriverCaller) {
            return null;
        }
        return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }

    private static DeliveryTrackingResource toResource(DeliveryTrackingQuery.TrackingSnapshot snapshot) {
        return new DeliveryTrackingResource(snapshot.deliveryId(), snapshot.providerId(), snapshot.driverId(),
                snapshot.lastLatitude(), snapshot.lastLongitude(), snapshot.lastAccuracyMeters(),
                snapshot.lastPositionAt(), snapshot.lastPositionEvidenceId(), snapshot.loaded(),
                snapshot.lastLoadMilestone(), snapshot.lastLoadAt(), snapshot.lastLoadVolume(),
                snapshot.lastLoadUnit(), snapshot.lastLoadEvidenceId(), snapshot.version());
    }
}
