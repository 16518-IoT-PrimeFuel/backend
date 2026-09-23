package com.primefuel.fulltank.platform.fulfillment.api;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import java.util.Optional;

/**
 * Public write seam of the delivery module for S15/T15-A. The assignment orchestrator (in
 * {@code applicationflows}) uses it to materialise a delivery that is <em>assigned but not started</em>:
 * starting is the physical machine's job (T14-A), never the orchestrator's.
 *
 * <p>Each delivery created here carries its {@code assignmentCommandId}, unique when present, so a retried
 * command finds the same delivery instead of creating a second one.
 */
public interface DeliveryAssignments {

    /** The delivery produced by an assignment command, if the command already ran. */
    Optional<DeliveryAssignmentSnapshot> findByAssignmentCommandId(String assignmentCommandId);

    /** Creates the delivery already in the {@code ASSIGNED} physical state (assignment ≠ start). */
    Result<DeliveryAssignmentSnapshot, ApplicationError> createAssigned(CreateAssignedDeliveryCommand command);

    /**
     * Creates a delivery the legacy way: {@code DISPATCHED} legacy status, no physical state written (it is
     * derived on read), for the v1 direct-order branch of {@link DeliveryIntegration}.
     */
    Result<DeliveryAssignmentSnapshot, ApplicationError> createLegacy(CreateLegacyDeliveryCommand command);

    record CreateLegacyDeliveryCommand(
            String assignmentCommandId,
            Long orderId,
            Long providerId,
            Long driverId,
            Long vehicleId,
            String scheduledDate,
            String notes) {
    }

    record CreateAssignedDeliveryCommand(
            String assignmentCommandId,
            Long orderId,
            Long providerId,
            Long driverId,
            Long vehicleId,
            String scheduledDate,
            String notes) {
    }

    record DeliveryAssignmentSnapshot(
            Long id,
            Long orderId,
            Long providerId,
            Long driverId,
            Long vehicleId,
            String physicalState,
            String status,
            Double requestedVolume,
            int version) {
    }
}
