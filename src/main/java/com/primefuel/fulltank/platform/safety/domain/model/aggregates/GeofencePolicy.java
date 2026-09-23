package com.primefuel.fulltank.platform.safety.domain.model.aggregates;

import com.primefuel.fulltank.platform.safety.domain.model.commands.CreateGeofencePolicyCommand;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A versioned geofence policy for a delivery (S17/U09): a circle (centre + radius) that a safety decision is
 * evaluated against. There is exactly one geometry — a circle, not a polygon (U09).
 *
 * <p><strong>Append-only.</strong> A policy is never edited in place: changing the geofence produces a new
 * row with the next {@code policyVersion}, superseding the previous one while the old row is kept. A
 * decision that was already persisted keeps referencing the version it was taken under, so history is never
 * rewritten.
 *
 * <p>The destination coordinates live in the policy itself rather than depending on {@code equipment}
 * exposing them: today a {@code Tank} has no coordinates, and S17 must not block on that.
 */
@Getter
@Setter
@NoArgsConstructor
public class GeofencePolicy extends AbstractDomainAggregateRoot<GeofencePolicy> {

    private Long id;
    private Long deliveryId;
    private Long providerId;
    private double centerLatitude;
    private double centerLongitude;
    private double radiusMeters;
    private int policyVersion;

    public GeofencePolicy(CreateGeofencePolicyCommand command, int policyVersion) {
        if (command.deliveryId() == null) {
            throw new IllegalArgumentException("A delivery is required");
        }
        if (command.providerId() == null) {
            throw new IllegalArgumentException("A provider is required");
        }
        if (command.centerLatitude() == null || !Double.isFinite(command.centerLatitude())
                || command.centerLatitude() < -90.0 || command.centerLatitude() > 90.0) {
            throw new IllegalArgumentException("The centre latitude must be within [-90, 90]");
        }
        if (command.centerLongitude() == null || !Double.isFinite(command.centerLongitude())
                || command.centerLongitude() < -180.0 || command.centerLongitude() > 180.0) {
            throw new IllegalArgumentException("The centre longitude must be within [-180, 180]");
        }
        if (command.radiusMeters() == null || !Double.isFinite(command.radiusMeters())
                || command.radiusMeters() <= 0) {
            throw new IllegalArgumentException("The radius must be a positive number of metres");
        }
        this.deliveryId = command.deliveryId();
        this.providerId = command.providerId();
        this.centerLatitude = command.centerLatitude();
        this.centerLongitude = command.centerLongitude();
        this.radiusMeters = command.radiusMeters();
        this.policyVersion = policyVersion;
    }
}
