package com.primefuel.fulltank.platform.safety.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.safety.api.GeofencePolicies;
import com.primefuel.fulltank.platform.safety.domain.model.commands.CreateGeofencePolicyCommand;
import com.primefuel.fulltank.platform.safety.interfaces.rest.resources.CreateGeofencePolicyResource;
import com.primefuel.fulltank.platform.safety.interfaces.rest.resources.GeofencePolicyResource;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
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
 * v2 geofence-policy administration (S17). It creates a new <em>version</em> of the circle a delivery's
 * safety decision is evaluated against; it never edits the previous version in place.
 *
 * <p><strong>It is a decision input, not a prevention mechanism.</strong> This endpoint configures the
 * geofence; whether it authorizes or blocks a valve is a separate, non-physical decision (see
 * {@code GeofenceEvaluation}). Nothing here prevents a physical event.
 *
 * <p>Restricted to the delivery's owning provider: the tenant comes from the principal
 * ({@code iam.api.TenantAccess}) and the delivery, never from the body. A delivery the caller does not own
 * is a 403, a missing delivery a 404.
 */
@RestController
@RequestMapping(value = "/api/v2/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Geofence policy", description = "Geofence-policy administration (v2, decision input)")
public class GeofencePoliciesController {

    private final GeofencePolicies geofencePolicies;
    private final DeliveryTrackingLookup deliveryTrackingLookup;
    private final TenantAccess tenantAccess;

    public GeofencePoliciesController(GeofencePolicies geofencePolicies,
                                      DeliveryTrackingLookup deliveryTrackingLookup,
                                      TenantAccess tenantAccess) {
        this.geofencePolicies = geofencePolicies;
        this.deliveryTrackingLookup = deliveryTrackingLookup;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Creates a new geofence-policy version for a delivery.
     *
     * <p>The provider is resolved server-side; a foreign delivery is a 403. The radius is a circle in metres;
     * invalid geometry is a 400.
     */
    @Operation(summary = "Create a geofence-policy version for a delivery",
            description = "Creates a new version of the delivery's geofence circle, owned by the caller's provider tenant; the previous version is kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Policy version created."),
            @ApiResponse(responseCode = "400", description = "The geometry is invalid (missing/invalid centre or non-positive radius)."),
            @ApiResponse(responseCode = "403", description = "The caller has no provider identity, or does not own the delivery."),
            @ApiResponse(responseCode = "404", description = "The delivery does not exist."),
            @ApiResponse(responseCode = "409", description = "A concurrent policy version already exists for this delivery.")
    })
    @PostMapping("/{deliveryId}/geofence-policies")
    public ResponseEntity<?> create(@PathVariable Long deliveryId,
                                    @Valid @RequestBody CreateGeofencePolicyResource resource) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var delivery = deliveryTrackingLookup.findAssignedDelivery(deliveryId);
        if (delivery.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (!providerId.get().equals(delivery.get().providerId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = geofencePolicies.createPolicy(new CreateGeofencePolicyCommand(
                deliveryId, providerId.get(), resource.centerLatitude(), resource.centerLongitude(),
                resource.radiusMeters()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, GeofencePoliciesController::toResource, HttpStatus.CREATED);
    }

    private static GeofencePolicyResource toResource(GeofencePolicies.PolicySnapshot policy) {
        return new GeofencePolicyResource(policy.id(), policy.deliveryId(), policy.providerId(),
                policy.centerLatitude(), policy.centerLongitude(), policy.radiusMeters(),
                policy.policyVersion());
    }
}
