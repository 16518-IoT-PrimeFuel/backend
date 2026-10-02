package com.primefuel.fulltank.platform.applicationflows;

import java.time.Instant;

public record DeliveryRecommendationQuery(
        Long providerId, Long orderId, Instant windowStart, Instant windowEnd) {}
