package com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates;

import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryStatus;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A delivery. Alongside the legacy {@link DeliveryStatus} (kept for v1 compatibility) it now tracks the
 * physical lifecycle (S14/T14-A) in {@link DeliveryPhysicalState}, plus an optimistic-lock {@code version}
 * and the physical timestamps/volumes.
 *
 * <p>{@code physicalState} is nullable on purpose: rows created by the legacy flow predate the machine and
 * are <em>derived</em> on read through {@link DeliveryPhysicalState#fromLegacy(DeliveryStatus)} instead of
 * being rewritten. The legacy {@code dispatch()/complete()/fail()} mutators are left untouched — T14-B is
 * the ticket that reroutes v1 through this machine.
 */
@Getter
@Setter
@NoArgsConstructor
public class Delivery extends AbstractDomainAggregateRoot<Delivery> {

    private Long id;
    private Long orderId;
    private Long providerId;
    private Long driverId;
    private Long vehicleId;
    private DeliveryStatus status;
    private String scheduledDate;
    private LocalDateTime dispatchedAt;
    private LocalDateTime deliveredAt;
    private String notes;

    private DeliveryPhysicalState physicalState;
    private LocalDateTime startedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime deliveringAt;
    private Double requestedVolume;
    private Double deliveredVolume;
    private int version;

    /**
     * The correlation id of the orchestrated assignment that created this delivery (T15-A), unique when
     * present. A retry of the same command finds the delivery here instead of creating a second one; legacy
     * (v1) deliveries carry none.
     */
    private String assignmentCommandId;

    public Delivery(CreateDeliveryCommand command) {
        this.orderId = command.orderId();
        this.providerId = command.providerId();
        this.driverId = command.driverId();
        this.vehicleId = command.vehicleId();
        this.scheduledDate = command.scheduledDate();
        this.notes = command.notes();
        this.status = DeliveryStatus.SCHEDULED;
    }

    public void dispatch() {
        this.status = DeliveryStatus.DISPATCHED;
        this.dispatchedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        this.status = DeliveryStatus.FAILED;
        this.notes = reason;
    }

    /** The physical state, derived from the legacy status when the row has not entered the machine yet. */
    public DeliveryPhysicalState currentPhysicalState() {
        return physicalState != null ? physicalState : DeliveryPhysicalState.fromLegacy(status);
    }

    /**
     * Materializes the {@code ASSIGNED} state. Returns {@code true} only when it was newly assigned, so
     * callers publish the event exactly once (a retry is an idempotent no-op, not a duplicate).
     */
    public boolean assign() {
        if (physicalState == DeliveryPhysicalState.ASSIGNED) {
            return false;
        }
        if (physicalState != null) {
            throw new IllegalStateException("Illegal delivery transition: " + physicalState + " -> ASSIGNED");
        }
        materialize(DeliveryPhysicalState.ASSIGNED);
        return true;
    }

    public void start() {
        transitionTo(DeliveryPhysicalState.STARTED);
        this.startedAt = LocalDateTime.now();
    }

    public void arrive() {
        transitionTo(DeliveryPhysicalState.ARRIVED);
        this.arrivedAt = LocalDateTime.now();
    }

    public void beginDelivering() {
        transitionTo(DeliveryPhysicalState.DELIVERING);
        this.deliveringAt = LocalDateTime.now();
    }

    /**
     * Closes the delivery with the delivered volume as evidence (U11). A partial delivery is legal:
     * {@code deliveredVolume ≤ requestedVolume} is accepted, both values are kept, and equality is never
     * required. A missing/non-positive delivered volume, a missing requested volume, or one exceeding the
     * requested volume, is rejected.
     */
    public void completePhysical(Double deliveredVolume, Double requestedVolume) {
        validateEvidence(deliveredVolume, requestedVolume);
        transitionTo(DeliveryPhysicalState.COMPLETED);
        this.requestedVolume = requestedVolume;
        this.deliveredVolume = deliveredVolume;
        this.deliveredAt = LocalDateTime.now();
    }

    /**
     * The evidence rules of a physical close (U11), exposed so the command service can reject a bad volume
     * <em>before</em> touching the delivery — otherwise a rejected close could leave it mid-discharge.
     *
     * <p>The requested volume is required, not optional: U11 keeps <em>both</em> values, so a close with an
     * unresolvable requested volume would persist an ambiguous {@code requestedVolume=null} beside a real
     * delivered one. That is exactly the state the invariant exists to prevent, so it is rejected.
     */
    public static void validateEvidence(Double deliveredVolume, Double requestedVolume) {
        if (deliveredVolume == null || deliveredVolume <= 0) {
            throw new IllegalArgumentException("A delivered volume greater than zero is required");
        }
        if (requestedVolume == null) {
            throw new IllegalArgumentException("The requested volume is required as evidence");
        }
        if (deliveredVolume > requestedVolume) {
            throw new IllegalArgumentException("The delivered volume cannot exceed the requested volume");
        }
    }

    public void failPhysical(String reason) {
        transitionTo(DeliveryPhysicalState.FAILED);
        this.notes = reason;
    }

    public void cancel(String reason) {
        transitionTo(DeliveryPhysicalState.CANCELLED);
        this.notes = reason;
    }

    private void transitionTo(DeliveryPhysicalState next) {
        var from = currentPhysicalState();
        if (!from.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal delivery transition: " + from + " -> " + next);
        }
        materialize(next);
    }

    private void materialize(DeliveryPhysicalState next) {
        this.physicalState = next;
        // Keep the legacy status coherent for v1 readers through the compatibility map.
        this.status = next.toLegacyStatus();
    }
}
