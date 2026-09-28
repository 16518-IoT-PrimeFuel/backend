package com.primefuel.fulltank.platform.fleet.infrastructure.services;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.api.events.ResourceDisabledEvent;
import com.primefuel.fulltank.platform.fleet.api.events.ResourceEnabledEvent;
import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.Driver;
import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.Tanker;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.repositories.DriverRepository;
import com.primefuel.fulltank.platform.fleet.domain.repositories.TankerRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Implements the public fleet write surface: registration, tenant-safe update and explicit enable/disable.
 * Disabling never deletes; it flips the lifecycle flag and publishes {@link ResourceDisabledEvent}.
 */
@Component("fleetRegistry")
public class FleetRegistryImpl implements FleetRegistry {

    private final DriverRepository driverRepository;
    private final TankerRepository tankerRepository;
    private final ApplicationEventPublisher events;

    public FleetRegistryImpl(DriverRepository driverRepository,
                             TankerRepository tankerRepository,
                             ApplicationEventPublisher events) {
        this.driverRepository = driverRepository;
        this.tankerRepository = tankerRepository;
        this.events = events;
    }

    @Override
    @Transactional
    public Result<FleetCatalog.DriverSnapshot, ApplicationError> registerDriver(RegisterDriverCommand command) {
        try {
            var driver = driverRepository.save(new Driver(command));
            return Result.success(FleetCatalogImpl.toSnapshot(driver));
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("driver", exception.getMessage()));
        }
    }

    @Override
    @Transactional
    public Result<FleetCatalog.DriverSnapshot, ApplicationError> updateDriver(UpdateDriverCommand command) {
        var existing = driverRepository.findById(command.driverId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Driver", String.valueOf(command.driverId())));
        }
        var driver = existing.get();
        try {
            driver.update(command);
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("driver", exception.getMessage()));
        }
        return Result.success(FleetCatalogImpl.toSnapshot(driverRepository.save(driver)));
    }

    @Override
    @Transactional
    public Result<FleetCatalog.DriverSnapshot, ApplicationError> deactivateDriver(Long driverId) {
        var existing = driverRepository.findById(driverId);
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Driver", String.valueOf(driverId)));
        }
        var driver = existing.get();
        driver.deactivate();
        var saved = driverRepository.save(driver);
        events.publishEvent(new ResourceDisabledEvent("DRIVER", saved.getId(), saved.getProviderId(), Instant.now()));
        return Result.success(FleetCatalogImpl.toSnapshot(saved));
    }

    @Override
    @Transactional
    public Result<FleetCatalog.DriverSnapshot, ApplicationError> activateDriver(Long driverId) {
        var existing = driverRepository.findById(driverId);
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Driver", String.valueOf(driverId)));
        }
        var driver = existing.get();
        driver.activate();
        var saved = driverRepository.save(driver);
        events.publishEvent(new ResourceEnabledEvent("DRIVER", saved.getId(), saved.getProviderId(), Instant.now()));
        return Result.success(FleetCatalogImpl.toSnapshot(saved));
    }

    @Override
    @Transactional
    public Result<FleetCatalog.TankerSnapshot, ApplicationError> registerTanker(RegisterTankerCommand command) {
        try {
            var tanker = tankerRepository.save(new Tanker(command));
            return Result.success(FleetCatalogImpl.toSnapshot(tanker));
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("tanker", exception.getMessage()));
        }
    }

    @Override
    @Transactional
    public Result<FleetCatalog.TankerSnapshot, ApplicationError> updateTanker(UpdateTankerCommand command) {
        var existing = tankerRepository.findById(command.tankerId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Tanker", String.valueOf(command.tankerId())));
        }
        var tanker = existing.get();
        try {
            tanker.update(command);
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("tanker", exception.getMessage()));
        }
        return Result.success(FleetCatalogImpl.toSnapshot(tankerRepository.save(tanker)));
    }

    @Override
    @Transactional
    public Result<FleetCatalog.TankerSnapshot, ApplicationError> deactivateTanker(Long tankerId) {
        var existing = tankerRepository.findById(tankerId);
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Tanker", String.valueOf(tankerId)));
        }
        var tanker = existing.get();
        tanker.deactivate();
        var saved = tankerRepository.save(tanker);
        events.publishEvent(new ResourceDisabledEvent("TANKER", saved.getId(), saved.getProviderId(), Instant.now()));
        return Result.success(FleetCatalogImpl.toSnapshot(saved));
    }

    @Override
    @Transactional
    public Result<FleetCatalog.TankerSnapshot, ApplicationError> activateTanker(Long tankerId) {
        var existing = tankerRepository.findById(tankerId);
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Tanker", String.valueOf(tankerId)));
        }
        var tanker = existing.get();
        tanker.activate();
        var saved = tankerRepository.save(tanker);
        events.publishEvent(new ResourceEnabledEvent("TANKER", saved.getId(), saved.getProviderId(), Instant.now()));
        return Result.success(FleetCatalogImpl.toSnapshot(saved));
    }
}
