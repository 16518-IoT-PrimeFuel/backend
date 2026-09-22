package com.primefuel.fulltank.platform.fleet.api;

import com.primefuel.fulltank.platform.fleet.domain.model.commands.ReserveFleetCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import java.time.Instant;

/**
 * Write seam for fleet reservations (S13/T13-A). Nothing outside the {@code fleet} module may hold a driver
 * or a tanker directly; this is the only public write surface for a temporal reservation.
 *
 * <p>{@link #reserve} models the window/volume, checks capacity and revalidates overlap against existing
 * active reservations while holding a {@code PESSIMISTIC_WRITE} lock on the reserved resources (U06). The
 * end-to-end concurrent barrier (two races → one winner) and the idempotent release/expiry lifecycle are
 * T13-B; {@link #reserve} is correct for a single caller and ready for that barrier.
 */
public interface FleetReservations {

    Result<ReservationSnapshot, ApplicationError> reserve(ReserveFleetCommand command);

    record ReservationSnapshot(
            Long id,
            Long providerId,
            Long driverId,
            Long tankerId,
            String reference,
            Instant windowStart,
            Instant windowEnd,
            double volume,
            String unit,
            String status) {
    }
}
