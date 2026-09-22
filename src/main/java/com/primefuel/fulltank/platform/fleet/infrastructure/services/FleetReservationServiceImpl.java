package com.primefuel.fulltank.platform.fleet.infrastructure.services;

import com.primefuel.fulltank.platform.fleet.api.FleetReservations;
import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.FleetReservation;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.ReserveFleetCommand;
import com.primefuel.fulltank.platform.fleet.domain.repositories.FleetReservationRepository;
import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.repositories.DriverPersistenceRepository;
import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.repositories.TankerPersistenceRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * T13-A reservation service. It models the window/volume, validates the reserved resources belong to the
 * caller's tenant, checks usable capacity against the volume and revalidates overlap — all while holding a
 * {@code PESSIMISTIC_WRITE} lock on the driver and tanker rows, in the same transaction (U06).
 *
 * <p>Not full T13-B: the lock + revalidation point are here, but the concurrent race proof (two callers,
 * one winner), the idempotent release and window expiry are T13-B. The lock order is fixed (driver, then
 * tanker) so two callers sharing a resource serialise instead of deadlocking.
 */
@Component("fleetReservations")
public class FleetReservationServiceImpl implements FleetReservations {

    private final FleetReservationRepository reservationRepository;
    private final DriverPersistenceRepository driverPersistenceRepository;
    private final TankerPersistenceRepository tankerPersistenceRepository;

    public FleetReservationServiceImpl(FleetReservationRepository reservationRepository,
                                       DriverPersistenceRepository driverPersistenceRepository,
                                       TankerPersistenceRepository tankerPersistenceRepository) {
        this.reservationRepository = reservationRepository;
        this.driverPersistenceRepository = driverPersistenceRepository;
        this.tankerPersistenceRepository = tankerPersistenceRepository;
    }

    @Override
    @Transactional
    public Result<ReservationSnapshot, ApplicationError> reserve(ReserveFleetCommand command) {
        FleetReservation reservation;
        try {
            reservation = new FleetReservation(command);
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("fleetReservation", exception.getMessage()));
        }

        // U06: lock the reserved resources themselves (driver, then tanker), never a global lock, and
        // revalidate overlap inside this same transaction.
        var driver = driverPersistenceRepository.lockById(command.driverId());
        if (driver.isEmpty() || !command.providerId().equals(driver.get().getProviderId())) {
            return Result.failure(ApplicationError.notFound("Driver", String.valueOf(command.driverId())));
        }
        var tanker = tankerPersistenceRepository.lockById(command.tankerId());
        if (tanker.isEmpty() || !command.providerId().equals(tanker.get().getProviderId())) {
            return Result.failure(ApplicationError.notFound("Tanker", String.valueOf(command.tankerId())));
        }

        var capacity = Volume.of(tanker.get().getCapacity(), Unit.fromCode(tanker.get().getUnit()));
        if (!capacity.atLeast(reservation.getVolume())) {
            return Result.failure(ApplicationError.conflict("FleetReservation",
                    "The tanker capacity is below the reserved volume"));
        }

        var overlapping = reservationRepository.findActiveOverlapping(command.providerId(), command.driverId(),
                command.tankerId(), reservation.getWindow().start(), reservation.getWindow().end());
        if (!overlapping.isEmpty()) {
            return Result.failure(ApplicationError.conflict("FleetReservation",
                    "The resource is already reserved in an overlapping window"));
        }

        return Result.success(toSnapshot(reservationRepository.save(reservation)));
    }

    private static ReservationSnapshot toSnapshot(FleetReservation reservation) {
        return new ReservationSnapshot(reservation.getId(), reservation.getProviderId(),
                reservation.getDriverId(), reservation.getTankerId(), reservation.getReference(),
                reservation.getWindow().start(), reservation.getWindow().end(),
                reservation.getVolume().amount(), reservation.getVolume().unit().name(),
                reservation.getStatus().name());
    }
}
