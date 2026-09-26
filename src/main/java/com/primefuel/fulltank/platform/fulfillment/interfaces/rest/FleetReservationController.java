package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.ports.FleetReservation;
import com.primefuel.fulltank.platform.fulfillment.application.ports.FleetReservationStore;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.CreateFleetReservationResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.FleetReservationResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/fleet/reservations")
public class FleetReservationController {
    private final FleetReservationStore reservations;

    public FleetReservationController(FleetReservationStore reservations) {
        this.reservations = reservations;
    }

    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsProvider(#resource.providerId())")
    public ResponseEntity<FleetReservationResource> reserve(@RequestBody CreateFleetReservationResource resource) {
        if (resource.idempotencyKey() == null || resource.idempotencyKey().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        var reserved = reservations.reserve(new FleetReservation(resource.idempotencyKey(), resource.providerId(),
                resource.deliveryId(), resource.driverId(), resource.vehicleId(), resource.windowStart(),
                resource.windowEnd(), resource.volume()));
        var response = new FleetReservationResource(resource.idempotencyKey(), reserved);
        return ResponseEntity.status(reserved ? HttpStatus.CREATED : HttpStatus.CONFLICT).body(response);
    }

    @PostMapping("/{idempotencyKey}/release")
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public ResponseEntity<Void> release(@PathVariable String idempotencyKey, @RequestParam Long providerId) {
        return reservations.release(providerId, idempotencyKey)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
