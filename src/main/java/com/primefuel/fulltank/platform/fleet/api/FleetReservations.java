package com.primefuel.fulltank.platform.fleet.api;

import com.primefuel.fulltank.platform.fleet.domain.model.commands.ReserveFleetCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import java.time.Instant;

/**
 * Write seam for fleet reservations (S13/T13-A/B). Nothing outside the {@code fleet} module may hold a
 * driver or a tanker directly; this is the only public write surface for a temporal reservation.
 *
 * <p>{@link #reserve} models the window/volume, checks capacity and revalidates overlap against existing
 * active reservations while holding a {@code PESSIMISTIC_WRITE} lock on the reserved resources (U06), so
 * two callers racing for the same resource serialise to one winner and one {@code 409}. It is idempotent
 * by the caller's {@code reference} (A3): a replay returns the same reservation, a mismatching replay is a
 * {@code 409}. {@link #release} and {@link #expireOverdue} close the lifecycle (A2).
 *
 * <p>No REST endpoint is exposed (A1): the reservation is consumed in-process by the fleet's next
 * consumer, so callers — never a request body — must supply {@code providerId} resolved from
 * {@code iam.api.TenantAccess} (A4).
 */
public interface FleetReservations {

    Result<ReservationSnapshot, ApplicationError> reserve(ReserveFleetCommand command);

    /**
     * Releases the reservation holding {@code reference}. Idempotent: releasing an already terminal
     * reservation returns its current snapshot unchanged. Unknown reference → {@code * _NOT_FOUND}.
     */
    Result<ReservationSnapshot, ApplicationError> release(String reference);

    /**
     * Expires every {@code ACTIVE} reservation whose window has already ended at the injected clock's now.
     * Deterministic and idempotent; returns how many reservations transitioned in this call.
     */
    Result<Integer, ApplicationError> expireOverdue();

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
