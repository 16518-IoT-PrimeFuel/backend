package com.primefuel.fulltank.platform.safety.valve.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.safety.valve.application.ValveObservationService;
import com.primefuel.fulltank.platform.safety.valve.interfaces.rest.resources.ValveObservationResource;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ErrorResponseAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Receives logical driver-app observations; it does not communicate with physical hardware. */
@RestController
@RequestMapping("/api/v2/deliveries")
@Tag(name = "Valve observations", description = "Logical valve ACK and safety observations")
public class ValveObservationsController {
    private final ValveObservationService observations;
    private final DeliveryTrackingLookup deliveries;
    private final FleetCatalog fleet;
    private final MembershipAccess membership;

    public ValveObservationsController(ValveObservationService observations, DeliveryTrackingLookup deliveries,
            FleetCatalog fleet, MembershipAccess membership) {
        this.observations = observations; this.deliveries = deliveries; this.fleet = fleet; this.membership = membership;
    }

    /** Records a valve state reported by the assigned driver and reconciles any pending OPEN command. */
    @Operation(summary = "Report a valve observation", description = "The assigned driver reports OPEN or CLOSED; invalid OPEN acknowledgements are recorded as safety incidents.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Observation recorded or ACK replayed idempotently."),
            @ApiResponse(responseCode = "202", description = "Unmatched OPEN recorded as a safety incident."),
            @ApiResponse(responseCode = "400", description = "Invalid observation body."),
            @ApiResponse(responseCode = "403", description = "Caller is not the assigned driver or assignment crosses tenants."),
            @ApiResponse(responseCode = "404", description = "Delivery or assigned driver does not exist.")
    })
    @PostMapping("/{deliveryId}/valve-observations")
    public ResponseEntity<?> observe(@PathVariable Long deliveryId, @Valid @RequestBody ValveObservationResource body) {
        var userId = membership.currentUserId();
        if (userId.isEmpty()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        var assigned = deliveries.findAssignedDelivery(deliveryId);
        if (assigned.isEmpty()) return ResponseEntity.notFound().build();
        var driver = fleet.findDriver(assigned.get().driverId());
        if (driver.isEmpty()) return ResponseEntity.notFound().build();
        if (!assigned.get().providerId().equals(driver.get().providerId()) || driver.get().userId() == null
                || !driver.get().userId().equals(userId.get())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            boolean incident = observations.observe(deliveryId, assigned.get().providerId(), body.state(), body.observedAt(), body.commandId());
            return ResponseEntity.status(incident ? HttpStatus.ACCEPTED : HttpStatus.OK).build();
        } catch (IllegalArgumentException e) {
            return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                    ApplicationError.validationError("state", e.getMessage()));
        }
    }
}
