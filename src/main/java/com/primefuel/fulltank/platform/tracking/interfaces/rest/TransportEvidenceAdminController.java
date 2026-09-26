package com.primefuel.fulltank.platform.tracking.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.tracking.api.DeliveryTrackingQuery;
import com.primefuel.fulltank.platform.tracking.domain.repositories.DeliveryTrackingRepository;
import com.primefuel.fulltank.platform.tracking.domain.repositories.TransportEvidenceSampleRepository;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.TrackingSampleResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v2/admin/deliveries/{deliveryId}/transport-evidence",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Transport evidence retention", description = "Administrative export and deletion of GPS evidence")
public class TransportEvidenceAdminController {
    private final DeliveryTrackingLookup deliveryLookup;
    private final DeliveryTrackingQuery trackingQuery;
    private final TransportEvidenceSampleRepository samples;
    private final DeliveryTrackingRepository tracking;

    public TransportEvidenceAdminController(DeliveryTrackingLookup deliveryLookup,
                                            DeliveryTrackingQuery trackingQuery,
                                            TransportEvidenceSampleRepository samples,
                                            DeliveryTrackingRepository tracking) {
        this.deliveryLookup = deliveryLookup;
        this.trackingQuery = trackingQuery;
        this.samples = samples;
        this.tracking = tracking;
    }

    /** Deletes GPS evidence and its rebuildable projection; business transitions and safety decisions remain. */
    @DeleteMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Transactional
    @Operation(summary = "Delete transport evidence",
            description = "Removes only raw GPS samples and the tracking projection for the delivery.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Transport evidence deleted (idempotent)."),
            @ApiResponse(responseCode = "403", description = "Caller is not a platform administrator."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist.")
    })
    public ResponseEntity<Void> delete(@PathVariable Long deliveryId) {
        if (deliveryLookup.findAssignedDelivery(deliveryId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        samples.deleteByDeliveryId(deliveryId);
        tracking.deleteByDeliveryId(deliveryId);
        return ResponseEntity.noContent().build();
    }

    /** Exports evidence with the same fields and ordering as the delivery tracking samples query. */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Export transport evidence",
            description = "Returns GPS and load evidence ordered by recordedAt.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evidence exported; samples may be empty."),
            @ApiResponse(responseCode = "403", description = "Caller is not a platform administrator."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist.")
    })
    public ResponseEntity<?> export(@PathVariable Long deliveryId) {
        if (deliveryLookup.findAssignedDelivery(deliveryId).isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var resources = trackingQuery.samples(deliveryId).stream()
                .map(snapshot -> new TrackingSampleResource(snapshot.evidenceId(), snapshot.kind(),
                        snapshot.latitude(), snapshot.longitude(), snapshot.accuracyMeters(),
                        snapshot.milestone(), snapshot.volume(), snapshot.unit(), snapshot.recordedAt(),
                        snapshot.receivedAt(), snapshot.latestAdvanced()))
                .toList();
        return ResponseEntity.ok(new ExportResource(deliveryId, resources));
    }

    public record ExportResource(Long deliveryId, List<TrackingSampleResource> samples) { }
}
