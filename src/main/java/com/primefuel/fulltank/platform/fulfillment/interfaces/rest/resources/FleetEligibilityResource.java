package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import java.util.List;

public record FleetEligibilityResource(Long providerId, List<Long> driverIds, List<Long> vehicleIds) {
}
