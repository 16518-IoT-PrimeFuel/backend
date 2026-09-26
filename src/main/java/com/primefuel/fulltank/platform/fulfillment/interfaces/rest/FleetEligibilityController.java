package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DriverRepository;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.VehicleRepository;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.FleetEligibilityResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
@RequestMapping("/api/v2/fleet")
public class FleetEligibilityController {

    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final Clock clock = Clock.systemUTC();

    public FleetEligibilityController(DriverRepository drivers, VehicleRepository vehicles) {
        this.drivers = drivers;
        this.vehicles = vehicles;
    }

    @GetMapping("/eligible")
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public FleetEligibilityResource eligible(@RequestParam Long providerId,
                                             @RequestParam(required = false) Double volume) {
        var eligibleDrivers = drivers.findByProviderId(providerId).stream()
                .filter(driver -> driver.isEligibleAt(clock))
                .map(driver -> driver.getId())
                .toList();
        var eligibleVehicles = vehicles.findByProviderId(providerId).stream()
                .filter(vehicle -> vehicle.isEligible()
                        && (volume == null || vehicle.getCapacity() >= volume))
                .map(vehicle -> vehicle.getId())
                .toList();
        return new FleetEligibilityResource(providerId, eligibleDrivers, eligibleVehicles);
    }
}
