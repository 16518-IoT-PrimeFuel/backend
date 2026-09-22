package com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The physical lifecycle of a delivery (S14/T14-A): {@code ASSIGNED → STARTED → ARRIVED → DELIVERING →
 * COMPLETED}, plus two explicit terminal exits, {@code FAILED} and {@code CANCELLED}.
 *
 * <p>The legacy {@link DeliveryStatus} (SCHEDULED/DISPATCHED/DELIVERED/FAILED) is <em>not</em> replaced:
 * it is kept and kept coherent through the compatibility map below, so v1 readers see no regression while
 * the physical machine advances.
 */
public enum DeliveryPhysicalState {

    ASSIGNED,
    STARTED,
    ARRIVED,
    DELIVERING,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Map<DeliveryPhysicalState, Set<DeliveryPhysicalState>> ALLOWED = Map.of(
            ASSIGNED, EnumSet.of(STARTED, FAILED, CANCELLED),
            STARTED, EnumSet.of(ARRIVED, FAILED, CANCELLED),
            ARRIVED, EnumSet.of(DELIVERING, FAILED, CANCELLED),
            DELIVERING, EnumSet.of(COMPLETED, FAILED, CANCELLED),
            COMPLETED, EnumSet.noneOf(DeliveryPhysicalState.class),
            FAILED, EnumSet.noneOf(DeliveryPhysicalState.class),
            CANCELLED, EnumSet.noneOf(DeliveryPhysicalState.class));

    public boolean canTransitionTo(DeliveryPhysicalState next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    /** A terminal state accepts no further transition (retries of `complete` must not double-count). */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    /**
     * Compatibility map, legacy → physical. A delivery created by the legacy flow is already dispatched,
     * i.e. it has entered the physical machine at {@code ASSIGNED}.
     */
    public static DeliveryPhysicalState fromLegacy(DeliveryStatus status) {
        if (status == null) {
            return ASSIGNED;
        }
        return switch (status) {
            case SCHEDULED, DISPATCHED -> ASSIGNED;
            case DELIVERED -> COMPLETED;
            case FAILED -> FAILED;
        };
    }

    /**
     * Compatibility map, physical → legacy, so the v1 {@code status} keeps meaning something. The legacy
     * enum has no CANCELLED, so a cancellation is reported as FAILED (the reason is kept in the notes).
     */
    public DeliveryStatus toLegacyStatus() {
        return switch (this) {
            case ASSIGNED, STARTED, ARRIVED, DELIVERING -> DeliveryStatus.DISPATCHED;
            case COMPLETED -> DeliveryStatus.DELIVERED;
            case FAILED, CANCELLED -> DeliveryStatus.FAILED;
        };
    }
}
