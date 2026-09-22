package com.primefuel.fulltank.platform.supply.application.commandservices;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.supply.domain.model.aggregates.SupplyReservation;
import com.primefuel.fulltank.platform.supply.domain.model.commands.ReserveSupplyCommand;

public interface SupplyReservationService {

    Result<SupplyReservation, ApplicationError> reserve(ReserveSupplyCommand command);

    /** Idempotent: releasing an already released/reconciled reference succeeds and changes nothing. */
    Result<Long, ApplicationError> release(String reference);

    /** Marks active reservations for a reference as consumed. Idempotent. */
    Result<Long, ApplicationError> reconcile(String reference);
}
