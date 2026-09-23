package com.primefuel.fulltank.platform.safety.api;

import com.primefuel.fulltank.platform.safety.domain.model.commands.CreateGeofencePolicyCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import java.util.Optional;

/**
 * Public write/read seam of the geofence policy (S17). It is the only way to create a policy version or to
 * read the one in force; nothing outside {@code safety} may hold a policy row.
 *
 * <p>Creating a policy is <strong>append-only versioning</strong>: each call produces a new version for the
 * delivery, superseding the previous one without editing it. Callers (the REST adapter) must supply
 * {@code providerId} resolved from {@code iam.api.TenantAccess} and the delivery's tenant — never from a
 * request body.
 */
public interface GeofencePolicies {

    /** Creates the next immutable version of a delivery's geofence policy. */
    Result<PolicySnapshot, ApplicationError> createPolicy(CreateGeofencePolicyCommand command);

    /** The policy version currently in force for a delivery, if any. */
    Optional<PolicySnapshot> latestForDelivery(Long deliveryId);

    record PolicySnapshot(
            Long id,
            Long deliveryId,
            Long providerId,
            double centerLatitude,
            double centerLongitude,
            double radiusMeters,
            int policyVersion) {
    }
}
