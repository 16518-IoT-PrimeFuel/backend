package com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.FleetReservation;
import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.ReservationWindow;
import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.entities.FleetReservationPersistenceEntity;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;

public final class FleetReservationPersistenceAssembler {

    private FleetReservationPersistenceAssembler() {
    }

    public static FleetReservation toDomain(FleetReservationPersistenceEntity entity) {
        var reservation = new FleetReservation();
        reservation.setId(entity.getId());
        reservation.setProviderId(entity.getProviderId());
        reservation.setDriverId(entity.getDriverId());
        reservation.setTankerId(entity.getTankerId());
        reservation.setReference(entity.getReference());
        reservation.setWindow(new ReservationWindow(entity.getWindowStart(), entity.getWindowEnd()));
        reservation.setVolume(Volume.of(entity.getVolume(), Unit.fromCode(entity.getUnit())));
        reservation.setStatus(entity.getStatus());
        reservation.setVersion(entity.getVersion());
        return reservation;
    }

    public static FleetReservationPersistenceEntity toPersistence(FleetReservation reservation) {
        var entity = new FleetReservationPersistenceEntity();
        if (reservation.getId() != null) {
            entity.setId(reservation.getId());
        }
        entity.setProviderId(reservation.getProviderId());
        entity.setDriverId(reservation.getDriverId());
        entity.setTankerId(reservation.getTankerId());
        entity.setReference(reservation.getReference());
        entity.setWindowStart(reservation.getWindow().start());
        entity.setWindowEnd(reservation.getWindow().end());
        entity.setVolume(reservation.getVolume().amount());
        entity.setUnit(reservation.getVolume().unit().name());
        entity.setStatus(reservation.getStatus());
        entity.setVersion(reservation.getVersion());
        return entity;
    }
}
