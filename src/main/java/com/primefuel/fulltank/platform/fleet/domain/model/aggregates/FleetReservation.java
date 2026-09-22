package com.primefuel.fulltank.platform.fleet.domain.model.aggregates;

import com.primefuel.fulltank.platform.fleet.domain.model.commands.ReserveFleetCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.FleetReservationStatus;
import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.ReservationWindow;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An exclusive hold on a driver + tanker over a {@link ReservationWindow} at a requested {@link Volume}
 * (S13). Its invariants: same tenant, positive volume, a valid window, and no overlap against an existing
 * active reservation of the same resource. The window/volume are modeled on the shared {@code Unit}/{@code
 * Volume} over the legacy {@code Double} (U05), with no decimal migration.
 *
 * <p>The optimistic-lock {@code version} and the {@code reference} idempotency key are wired here; the
 * concurrent barrier that makes two races resolve to one winner, and release/expiry, are T13-B.
 */
@Getter
@Setter
@NoArgsConstructor
public class FleetReservation extends AbstractDomainAggregateRoot<FleetReservation> {

    private Long id;
    private Long providerId;
    private Long driverId;
    private Long tankerId;
    private String reference;
    private ReservationWindow window;
    private Volume volume;
    private FleetReservationStatus status;
    private int version;

    public FleetReservation(ReserveFleetCommand command) {
        if (command.providerId() == null) {
            throw new IllegalArgumentException("A provider is required");
        }
        if (command.driverId() == null) {
            throw new IllegalArgumentException("A driver is required");
        }
        if (command.tankerId() == null) {
            throw new IllegalArgumentException("A tanker is required");
        }
        if (command.volume() == null || command.volume() <= 0) {
            throw new IllegalArgumentException("The reserved volume must be positive");
        }
        this.providerId = command.providerId();
        this.driverId = command.driverId();
        this.tankerId = command.tankerId();
        this.reference = command.reference();
        this.window = new ReservationWindow(command.windowStart(), command.windowEnd());
        this.volume = Volume.of(command.volume(), Unit.fromCode(command.unit()));
        this.status = FleetReservationStatus.ACTIVE;
    }

    /** Whether this reservation's window overlaps another window for the same resource. */
    public boolean overlaps(ReservationWindow other) {
        return window.overlaps(other);
    }

    public boolean isActive() {
        return status == FleetReservationStatus.ACTIVE;
    }

    /**
     * Releases the hold. Idempotency of the release <em>operation</em> and its window-expiry sibling are
     * T13-B; the aggregate only guards that a terminal reservation is not released twice.
     */
    public void release() {
        if (status != FleetReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only an active reservation can be released");
        }
        this.status = FleetReservationStatus.RELEASED;
    }
}
