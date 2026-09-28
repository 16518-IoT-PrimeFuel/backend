package com.primefuel.fulltank.platform.supply.application.internal.commandservices;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.supply.api.SupplyCatalog;
import com.primefuel.fulltank.platform.supply.domain.model.aggregates.SupplyReservation;
import com.primefuel.fulltank.platform.supply.domain.model.commands.ReserveSupplyCommand;
import com.primefuel.fulltank.platform.supply.domain.model.valueobjects.ReservationStatus;
import com.primefuel.fulltank.platform.supply.domain.repositories.SupplyReservationRepository;
import com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.SupplyStockLockInitializer;
import com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.repositories.SupplyStockLockPersistenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplyReservationServiceImpl {

    private final SupplyCatalog supplyCatalog;
    private final SupplyReservationRepository reservationRepository;
    private final SupplyStockLockInitializer lockInitializer;
    private final SupplyStockLockPersistenceRepository lockRepository;

    public SupplyReservationServiceImpl(SupplyCatalog supplyCatalog,
                                        SupplyReservationRepository reservationRepository,
                                        SupplyStockLockInitializer lockInitializer,
                                        SupplyStockLockPersistenceRepository lockRepository) {
        this.supplyCatalog = supplyCatalog;
        this.reservationRepository = reservationRepository;
        this.lockInitializer = lockInitializer;
        this.lockRepository = lockRepository;
    }
    @Transactional
    public Result<SupplyReservation, ApplicationError> reserve(ReserveSupplyCommand command) {
        if (command.providerId() == null || command.fuelProductId() == null) {
            return Result.failure(ApplicationError.validationError("supply", "providerId and fuelProductId are required"));
        }
        if (command.quantity() == null || command.quantity() <= 0) {
            return Result.failure(ApplicationError.validationError("quantity", "Reserved quantity must be positive"));
        }
        var snapshot = supplyCatalog.findForTenant(command.providerId(), command.fuelProductId());
        if (snapshot.isEmpty()) {
            return Result.failure(ApplicationError.notFound("FuelProduct", String.valueOf(command.fuelProductId())));
        }

        lockInitializer.ensureExists(command.providerId(), command.fuelProductId());
        lockRepository.lockByProduct(command.providerId(), command.fuelProductId())
                .orElseThrow(() -> new IllegalStateException("Supply stock lock row missing after initialization"));

        var alreadyReserved = reservationRepository.findActiveByProduct(command.providerId(), command.fuelProductId())
                .stream().mapToDouble(SupplyReservation::getQuantity).sum();
        var available = snapshot.get().stock() - alreadyReserved;
        if (available + 1e-9 < command.quantity()) {
            return Result.failure(ApplicationError.conflict(
                    "Supply", "Insufficient available supply for the requested quantity"));
        }
        var reservation = new SupplyReservation(command, snapshot.get().pricePerUnit());
        return Result.success(reservationRepository.save(reservation));
    }
    @Transactional
    public Result<Long, ApplicationError> release(String reference) {
        return transition(reference, SupplyReservation::release);
    }
    @Transactional
    public Result<Long, ApplicationError> reconcile(String reference) {
        return transition(reference, SupplyReservation::reconcile);
    }

    private Result<Long, ApplicationError> transition(String reference,
                                                      java.util.function.Consumer<SupplyReservation> action) {
        if (reference == null || reference.isBlank()) {
            return Result.failure(ApplicationError.validationError("reference", "A reference is required"));
        }
        var active = reservationRepository.findByReferenceAndStatus(reference, ReservationStatus.ACTIVE);
        active.forEach(action);
        active.forEach(reservationRepository::save);
        return Result.success((long) active.size());
    }
}
