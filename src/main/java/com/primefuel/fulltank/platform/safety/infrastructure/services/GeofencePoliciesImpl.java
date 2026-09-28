package com.primefuel.fulltank.platform.safety.infrastructure.services;

import com.primefuel.fulltank.platform.safety.api.GeofencePolicies;
import com.primefuel.fulltank.platform.safety.domain.model.aggregates.GeofencePolicy;
import com.primefuel.fulltank.platform.safety.domain.model.commands.CreateGeofencePolicyCommand;
import com.primefuel.fulltank.platform.safety.domain.repositories.GeofencePolicyRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Append-only versioning of geofence policies (S17). Each creation bumps the delivery's policy version by
 * one and writes a new row; the previous row is never touched. A concurrent creation for the same delivery
 * collides on the {@code (delivery_id, policy_version)} unique and is reported as a conflict.
 */
@Component("geofencePolicies")
public class GeofencePoliciesImpl implements GeofencePolicies {

    private final GeofencePolicyRepository policyRepository;

    public GeofencePoliciesImpl(GeofencePolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    // Deliberately not @Transactional: the unique violation of a concurrent version is raised inside the
    // repository's own transaction, so it can be caught here without marking an outer transaction rollback-only
    // (which would turn the documented 409 into an UnexpectedRollbackException / 500).
    @Override
    public Result<PolicySnapshot, ApplicationError> createPolicy(CreateGeofencePolicyCommand command) {
        int nextVersion = policyRepository.findLatestByDeliveryId(command.deliveryId())
                .map(latest -> latest.getPolicyVersion() + 1)
                .orElse(1);
        GeofencePolicy policy;
        try {
            policy = new GeofencePolicy(command, nextVersion);
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("geofencePolicy", exception.getMessage()));
        }
        try {
            return Result.success(toSnapshot(policyRepository.save(policy)));
        } catch (DataIntegrityViolationException exception) {
            return Result.failure(ApplicationError.conflict("GeofencePolicy",
                    "A concurrent policy version already exists for this delivery"));
        }
    }

    @Override
    public Optional<PolicySnapshot> latestForDelivery(Long deliveryId) {
        if (deliveryId == null) {
            return Optional.empty();
        }
        return policyRepository.findLatestByDeliveryId(deliveryId).map(GeofencePoliciesImpl::toSnapshot);
    }

    static PolicySnapshot toSnapshot(GeofencePolicy policy) {
        return new PolicySnapshot(policy.getId(), policy.getDeliveryId(), policy.getProviderId(),
                policy.getCenterLatitude(), policy.getCenterLongitude(), policy.getRadiusMeters(),
                policy.getPolicyVersion());
    }
}
