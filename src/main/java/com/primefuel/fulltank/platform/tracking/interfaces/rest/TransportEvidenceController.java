package com.primefuel.fulltank.platform.tracking.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ErrorResponseAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import com.primefuel.fulltank.platform.tracking.api.TransportEvidenceRecorder;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordLoadEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordPositionEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.TransportEvidenceKind;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.TransportEvidenceAckResource;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.TransportEvidenceResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driver-app transport evidence (S16/T16-A, redesign W6). The driver's phone reports position pings and load
 * milestones over an ordinary authenticated v2 call — no IoT device, no device credential.
 *
 * <p><strong>Authorization:</strong> the caller must be the driver <em>assigned to that delivery</em>. The
 * assigned driver and its tenant are resolved from the delivery assignment (S14/S15) and the fleet catalog,
 * never from the body. A delivery the caller cannot see is a 404; an inconsistent cross-tenant assignment or
 * a caller who is not the assigned driver is a 403. An invalid body is a 400 and an impossible load sequence
 * is a 422.
 *
 * <p>A late position sample (older than the last one already stored) is accepted and kept as raw evidence,
 * but does not move the projection's latest value — the acknowledgement reports this through
 * {@code latestAdvanced}.
 */
@RestController
@RequestMapping(value = "/api/v2/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Transport evidence", description = "Driver-app-reported transport evidence (v2)")
public class TransportEvidenceController {

    private final TransportEvidenceRecorder transportEvidenceRecorder;
    private final DeliveryTrackingLookup deliveryTrackingLookup;
    private final FleetCatalog fleetCatalog;
    private final MembershipAccess membershipAccess;

    public TransportEvidenceController(TransportEvidenceRecorder transportEvidenceRecorder,
                                       DeliveryTrackingLookup deliveryTrackingLookup,
                                       FleetCatalog fleetCatalog,
                                       MembershipAccess membershipAccess) {
        this.transportEvidenceRecorder = transportEvidenceRecorder;
        this.deliveryTrackingLookup = deliveryTrackingLookup;
        this.fleetCatalog = fleetCatalog;
        this.membershipAccess = membershipAccess;
    }

    /**
     * Records a transport-evidence sample for a delivery.
     *
     * <p>Only the driver assigned to the delivery may report evidence; the assignment is resolved
     * server-side, so a body can never impersonate another driver. A late position sample is stored as raw
     * evidence without regressing the projection.</p>
     */
    @Operation(summary = "Record transport evidence for a delivery",
            description = "Accepts a driver-app position ping or load milestone for a delivery whose assigned driver is the caller; a late sample is preserved without moving the latest value.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replay: this eventId was already recorded for the delivery; the original ack is returned and nothing new is stored."),
            @ApiResponse(responseCode = "201", description = "Evidence recorded (latestAdvanced=false for a late sample)."),
            @ApiResponse(responseCode = "400", description = "The body is malformed (unknown type/milestone, missing coordinates, non-positive volume)."),
            @ApiResponse(responseCode = "403", description = "The caller is not the driver assigned to the delivery, or the assignment crosses tenants."),
            @ApiResponse(responseCode = "404", description = "The delivery (or its assigned driver) does not exist."),
            @ApiResponse(responseCode = "422", description = "The load milestone is not possible from the current state (e.g. UNLOADED before LOADED).")
    })
    @PostMapping("/{deliveryId}/transport-evidence")
    public ResponseEntity<?> record(@PathVariable Long deliveryId,
                                    @Valid @RequestBody TransportEvidenceResource resource) {
        var userId = membershipAccess.currentUserId();
        if (userId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

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

        // S16 invariant: driver and delivery share a tenant. A mismatch is an inconsistent assignment.
        if (!assignment.providerId().equals(assignedDriver.providerId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        // The caller must be the assigned driver (identity resolved from the principal, never the body).
        if (assignedDriver.userId() == null || !assignedDriver.userId().equals(userId.get())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        TransportEvidenceKind kind;
        try {
            kind = TransportEvidenceKind.fromCode(resource.type());
        } catch (IllegalArgumentException exception) {
            return error(ApplicationError.validationError("type", exception.getMessage()));
        }

        return switch (kind) {
            case POSITION -> respond(transportEvidenceRecorder.recordPosition(new RecordPositionEvidenceCommand(
                    assignment.deliveryId(), assignment.providerId(), assignment.driverId(),
                    resource.latitude(), resource.longitude(), resource.accuracyMeters(),
                    resource.recordedAt(), resource.eventId())));
            case LOAD -> recordLoad(assignment, resource);
        };
    }

    private ResponseEntity<?> recordLoad(DeliveryTrackingLookup.AssignedDeliverySnapshot assignment,
                                         TransportEvidenceResource resource) {
        LoadMilestone milestone;
        try {
            milestone = LoadMilestone.fromCode(resource.milestone());
        } catch (IllegalArgumentException exception) {
            return error(ApplicationError.validationError("milestone", exception.getMessage()));
        }
        return respond(transportEvidenceRecorder.recordLoad(new RecordLoadEvidenceCommand(
                assignment.deliveryId(), assignment.providerId(), assignment.driverId(), milestone,
                resource.volume(), resource.unit(), resource.recordedAt(), resource.eventId())));
    }

    /** 201 for a new sample, 200 when the {@code eventId} replays one already stored. */
    private static ResponseEntity<?> respond(Result<TransportEvidenceRecorder.EvidenceAck, ApplicationError> result) {
        var replayed = result instanceof Result.Success<TransportEvidenceRecorder.EvidenceAck, ApplicationError> s
                && s.value().replayed();
        return ResponseEntityAssembler.toResponseEntityFromResult(result, TransportEvidenceController::toResource,
                replayed ? HttpStatus.OK : HttpStatus.CREATED);
    }

    private static ResponseEntity<?> error(ApplicationError applicationError) {
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    private static TransportEvidenceAckResource toResource(TransportEvidenceRecorder.EvidenceAck ack) {
        return new TransportEvidenceAckResource(ack.evidenceId(), ack.deliveryId(), ack.kind(), ack.milestone(),
                ack.latestAdvanced(), ack.recordedAt());
    }
}
