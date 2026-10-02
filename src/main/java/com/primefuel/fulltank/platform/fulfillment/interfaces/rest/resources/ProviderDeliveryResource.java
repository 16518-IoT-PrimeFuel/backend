package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import java.time.Instant;

public record ProviderDeliveryResource(
        Long id,
        Long orderId,
        String status,
        String physicalState,
        DriverResource driver,
        TankerResource tanker,
        String scheduledDate,
        Instant windowStart,
        Instant windowEnd,
        Long buyerCompanyId,
        String buyerCompanyName,
        Long customerAccountId,
        Long siteId,
        String deliveryAddress,
        Double requestedVolume,
        String unit,
        Double deliveredVolume) {
    public record DriverResource(Long id, String firstName, String lastName) {}

    public record TankerResource(Long id, String licensePlate) {}
}
