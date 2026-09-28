package com.primefuel.fulltank.platform.fleet.infrastructure.services;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.Driver;
import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.Tanker;
import com.primefuel.fulltank.platform.fleet.domain.repositories.DriverRepository;
import com.primefuel.fulltank.platform.fleet.domain.repositories.TankerRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component("fleetCatalog")
public class FleetCatalogImpl implements FleetCatalog {

    private final DriverRepository driverRepository;
    private final TankerRepository tankerRepository;

    public FleetCatalogImpl(DriverRepository driverRepository, TankerRepository tankerRepository) {
        this.driverRepository = driverRepository;
        this.tankerRepository = tankerRepository;
    }

    @Override
    public Optional<DriverSnapshot> findDriver(Long driverId) {
        if (driverId == null) {
            return Optional.empty();
        }
        return driverRepository.findById(driverId).map(FleetCatalogImpl::toSnapshot);
    }

    @Override
    public List<DriverSnapshot> listDrivers(Long providerId) {
        if (providerId == null) {
            return List.of();
        }
        return driverRepository.findByProviderId(providerId).stream().map(FleetCatalogImpl::toSnapshot).toList();
    }

    @Override
    public Optional<TankerSnapshot> findTanker(Long tankerId) {
        if (tankerId == null) {
            return Optional.empty();
        }
        return tankerRepository.findById(tankerId).map(FleetCatalogImpl::toSnapshot);
    }

    @Override
    public List<TankerSnapshot> listTankers(Long providerId) {
        if (providerId == null) {
            return List.of();
        }
        return tankerRepository.findByProviderId(providerId).stream().map(FleetCatalogImpl::toSnapshot).toList();
    }

    static DriverSnapshot toSnapshot(Driver driver) {
        return new DriverSnapshot(
                driver.getId(), driver.getProviderId(), driver.getUserId(), driver.getFirstName(),
                driver.getLastName(), driver.getLicenseNumber(), driver.getPhoneNumber(), driver.getEmail(),
                driver.getStatus(), driver.isActive());
    }

    static TankerSnapshot toSnapshot(Tanker tanker) {
        return new TankerSnapshot(
                tanker.getId(), tanker.getProviderId(), tanker.getLicensePlate(), tanker.getBrand(),
                tanker.getModel(), tanker.getCapacity(), tanker.getUnit(), tanker.getStatus(), tanker.isActive());
    }
}
