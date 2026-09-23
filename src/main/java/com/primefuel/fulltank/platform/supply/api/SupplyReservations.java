package com.primefuel.fulltank.platform.supply.api;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.supply.domain.model.commands.ReserveSupplyCommand;

/**
 * Public write seam over supply reservations (S11/T11-B, consumed by S15/T15-A). Exclusivity of stock is
 * enforced inside the supply module (per-product {@code PESSIMISTIC_WRITE} lock); this is the only way
 * another module reserves supply, never through the inventory repository.
 */
public interface SupplyReservations {

    /** Reserves stock for a reference; capacity is revalidated under the product lock inside the module. */
    Result<ReservationSnapshot, ApplicationError> reserve(ReserveSupplyCommand command);

    /** Idempotent release of every active reservation of a reference. */
    Result<Long, ApplicationError> release(String reference);

    record ReservationSnapshot(
            Long id,
            Long providerId,
            Long fuelProductId,
            String reference,
            double quantity,
            String unit,
            String status) {
    }
}
