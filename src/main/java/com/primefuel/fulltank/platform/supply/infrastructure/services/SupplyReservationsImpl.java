package com.primefuel.fulltank.platform.supply.infrastructure.services;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.supply.api.SupplyReservations;
import com.primefuel.fulltank.platform.supply.application.commandservices.SupplyReservationService;
import com.primefuel.fulltank.platform.supply.domain.model.aggregates.SupplyReservation;
import com.primefuel.fulltank.platform.supply.domain.model.commands.ReserveSupplyCommand;
import org.springframework.stereotype.Component;

/** Adapter over {@link SupplyReservationService} exposing the reservation write surface as {@code supply.api}. */
@Component("supplyReservations")
public class SupplyReservationsImpl implements SupplyReservations {

    private final SupplyReservationService reservationService;

    public SupplyReservationsImpl(SupplyReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Override
    public Result<ReservationSnapshot, ApplicationError> reserve(ReserveSupplyCommand command) {
        return reservationService.reserve(command).map(SupplyReservationsImpl::toSnapshot);
    }

    @Override
    public Result<Long, ApplicationError> release(String reference) {
        return reservationService.release(reference);
    }

    private static ReservationSnapshot toSnapshot(SupplyReservation reservation) {
        return new ReservationSnapshot(reservation.getId(), reservation.getProviderId(),
                reservation.getFuelProductId(), reservation.getReference(), reservation.getQuantity(),
                reservation.getUnit(), reservation.getStatus() == null ? null : reservation.getStatus().name());
    }
}
