package com.primefuel.fulltank.platform.applicationflows;

import java.time.Instant;

public record DeliveryRecommendation(
        Long orderId,
        boolean recommended,
        String reason,
        Long driverId,
        String driverName,
        Long tankerId,
        String licensePlate,
        Double tankerCapacityLitres,
        double requestedVolumeLitres,
        Instant windowStart,
        Instant windowEnd,
        String criterion) {}
