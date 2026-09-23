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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Concurrency-safe fleet reservation service (S13/T13-A/B). It models the window/volume, validates the
 * reserved resources belong to the caller's tenant, checks usable capacity and revalidates overlap — all
 * while holding a {@code PESSIMISTIC_WRITE} lock on the driver and tanker rows, in the same transaction
 * (U06). The lock order is fixed (driver, then tanker) so two callers sharing a resource serialise instead
 * of deadlocking.
 *
 * <p>T13-B closes the two open ends of T13-A: the {@code reference} idempotency key (A3) and the
 * idempotent release/expiry lifecycle (A2). The {@code READ_COMMITTED} isolation is deliberate — the
 * reference lookup must observe a reservation committed by the caller that held the lock before us, or a
 * replay that lost the race would hit the unique constraint instead of replaying.
 */
@Component("fleetReservations")
public class FleetReservationServiceImpl implements FleetReservations {

    private final FleetReservationRepository reservationRepository;
    private final DriverPersistenceRepository driverPersistenceRepository;
    private final TankerPersistenceRepository tankerPersistenceRepository;
    private final Clock clock;

    public FleetReservationServiceImpl(FleetReservationRepository reservationRepository,
                                       DriverPersistenceRepository driverPersistenceRepository,
                                       TankerPersistenceRepository tankerPersistenceRepository,
                                       Clock clock) {
        this.reservationRepository = reservationRepository;
        this.driverPersistenceRepository = driverPersistenceRepository;
        this.tankerPersistenceRepository = tankerPersistenceRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<ReservationSnapshot, ApplicationError> reserve(ReserveFleetCommand command) {
        FleetReservation reservation;
        try {
            reservation = new FleetReservation(command);
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("fleetReservation", exception.getMessage()));
        }

        // U06: lock the reserved resources themselves (driver, then tanker), never a global lock, and
        // revalidate overlap inside this same transaction. The fixed order serialises callers that share a
        // resource instead of deadlocking them.
        var driver = driverPersistenceRepository.lockById(command.driverId());
        if (driver.isEmpty() || !command.providerId().equals(driver.get().getProviderId())) {
            return Result.failure(ApplicationError.notFound("Driver", String.valueOf(command.driverId())));
        }
        var tanker = tankerPersistenceRepository.lockById(command.tankerId());
        if (tanker.isEmpty() || !command.providerId().equals(tanker.get().getProviderId())) {
            return Result.failure(ApplicationError.notFound("Tanker", String.valueOf(command.tankerId())));
        }

        // A3: the reference is the idempotency key. Reading it *after* the locks is what makes a retry that
        // raced the original (and then waited on the lock) see the committed reservation and replay it,
        // rather than crawl under the unique constraint.
        var reference = command.reference();
        if (reference != null && !reference.isBlank()) {
            var existing = reservationRepository.findByReference(reference);
            if (existing.isPresent()) {
                return replayOrConflict(existing.get(), command);
            }
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

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<ReservationSnapshot, ApplicationError> release(String reference) {
        if (reference == null || reference.isBlank()) {
            return Result.failure(ApplicationError.validationError("reference", "A reference is required"));
        }
        var existing = reservationRepository.findByReference(reference);
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("FleetReservation", reference));
        }
        var reservation = existing.get();
        // Idempotent: a terminal reservation is returned as-is, never transitioned twice.
        if (reservation.isActive()) {
            reservation.release();
            reservation = reservationRepository.save(reservation);
        }
        return Result.success(toSnapshot(reservation));
    }

    @Override
    @Transactional
    public Result<Integer, ApplicationError> expireOverdue() {
        var now = clock.instant();
        var expired = 0;
        for (var reservation : reservationRepository.findActivePastDue(now)) {
            if (reservation.expireIfPast(now)) {
                reservationRepository.save(reservation);
                expired++;
            }
        }
        return Result.success(expired);
    }

    /** A replay is accepted only when it carries the exact same request; otherwise the reference is a conflict. */
    private Result<ReservationSnapshot, ApplicationError> replayOrConflict(FleetReservation existing,
                                                                           ReserveFleetCommand command) {
        if (matches(existing, command)) {
            return Result.success(toSnapshot(existing));
        }
        return Result.failure(ApplicationError.conflict("FleetReservation",
                "The reference is already used by a different reservation"));
    }

    private static boolean matches(FleetReservation existing, ReserveFleetCommand command) {
        return existing.getProviderId().equals(command.providerId())
                && existing.getDriverId().equals(command.driverId())
                && existing.getTankerId().equals(command.tankerId())
                && existing.getWindow().start().equals(command.windowStart())
                && existing.getWindow().end().equals(command.windowEnd())
                && Double.compare(existing.getVolume().amount(), command.volume()) == 0
                && existing.getVolume().unit() == Unit.fromCode(command.unit());
    }

    private static ReservationSnapshot toSnapshot(FleetReservation reservation) {
        return new ReservationSnapshot(reservation.getId(), reservation.getProviderId(),
                reservation.getDriverId(), reservation.getTankerId(), reservation.getReference(),
                reservation.getWindow().start(), reservation.getWindow().end(),
                reservation.getVolume().amount(), reservation.getVolume().unit().name(),
                reservation.getStatus().name());
    }
}
