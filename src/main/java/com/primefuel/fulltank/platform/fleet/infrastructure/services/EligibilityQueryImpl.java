package com.primefuel.fulltank.platform.fleet.infrastructure.services;

import com.primefuel.fulltank.platform.fleet.api.EligibilityQuery;
import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.Driver;
import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.Tanker;
import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.DriverStatus;
import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.TankerStatus;
import com.primefuel.fulltank.platform.fleet.domain.repositories.DriverRepository;
import com.primefuel.fulltank.platform.fleet.domain.repositories.TankerRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Implements U07: eligibility = allowed status + {@code active=true} + same tenant. It reads through the
 * module's own repositories; nothing outside {@code fleet} sees a driver/tanker row to compute this.
 */
@Component("eligibilityQuery")
public class EligibilityQueryImpl implements EligibilityQuery {

    private final DriverRepository driverRepository;
    private final TankerRepository tankerRepository;

    public EligibilityQueryImpl(DriverRepository driverRepository, TankerRepository tankerRepository) {
        this.driverRepository = driverRepository;
        this.tankerRepository = tankerRepository;
    }

    @Override
    public Optional<DriverAssessment> assessDriver(Long providerId, Long driverId) {
        if (providerId == null || driverId == null) {
            return Optional.empty();
        }
        return driverRepository.findById(driverId)
                .filter(driver -> providerId.equals(driver.getProviderId()))
                .map(driver -> assess(driver, providerId));
    }

    @Override
    public List<FleetCatalog.DriverSnapshot> eligibleDrivers(Long providerId) {
        if (providerId == null) {
            return List.of();
        }
        return driverRepository.findByProviderId(providerId).stream()
                .filter(driver -> assess(driver, providerId).outcome() == Outcome.ELIGIBLE)
                .map(FleetCatalogImpl::toSnapshot)
                .toList();
    }

    @Override
    public Optional<TankerAssessment> assessTanker(Long providerId, Long tankerId) {
        if (providerId == null || tankerId == null) {
            return Optional.empty();
        }
        return tankerRepository.findById(tankerId)
                .filter(tanker -> providerId.equals(tanker.getProviderId()))
                .map(tanker -> assess(tanker, providerId));
    }

    @Override
    public List<FleetCatalog.TankerSnapshot> eligibleTankers(Long providerId) {
        if (providerId == null) {
            return List.of();
        }
        return tankerRepository.findByProviderId(providerId).stream()
                .filter(tanker -> assess(tanker, providerId).outcome() == Outcome.ELIGIBLE)
                .map(FleetCatalogImpl::toSnapshot)
                .toList();
    }

    private static DriverAssessment assess(Driver driver, Long providerId) {
        if (!driver.isActive()) {
            return new DriverAssessment(driver.getId(), providerId, Outcome.INELIGIBLE, "disabled");
        }
        DriverStatus status;
        try {
            status = DriverStatus.fromCode(driver.getStatus());
        } catch (IllegalArgumentException exception) {
            return new DriverAssessment(driver.getId(), providerId, Outcome.INELIGIBLE,
                    "unknown status " + driver.getStatus());
        }
        return switch (status) {
            case AVAILABLE -> new DriverAssessment(driver.getId(), providerId, Outcome.ELIGIBLE, "available");
            case ASSIGNED -> new DriverAssessment(driver.getId(), providerId, Outcome.BUSY, "busy (assigned)");
            default -> new DriverAssessment(driver.getId(), providerId, Outcome.INELIGIBLE, "status " + status);
        };
    }

    private static TankerAssessment assess(Tanker tanker, Long providerId) {
        if (!tanker.isActive()) {
            return new TankerAssessment(tanker.getId(), providerId, Outcome.INELIGIBLE, "disabled");
        }
        TankerStatus status;
        try {
            status = TankerStatus.fromCode(tanker.getStatus());
        } catch (IllegalArgumentException exception) {
            return new TankerAssessment(tanker.getId(), providerId, Outcome.INELIGIBLE,
                    "unknown status " + tanker.getStatus());
        }
        return switch (status) {
            case AVAILABLE -> new TankerAssessment(tanker.getId(), providerId, Outcome.ELIGIBLE, "available");
            case IN_ROUTE -> new TankerAssessment(tanker.getId(), providerId, Outcome.BUSY, "busy (in route)");
            default -> new TankerAssessment(tanker.getId(), providerId, Outcome.INELIGIBLE, "status " + status);
        };
    }
}
