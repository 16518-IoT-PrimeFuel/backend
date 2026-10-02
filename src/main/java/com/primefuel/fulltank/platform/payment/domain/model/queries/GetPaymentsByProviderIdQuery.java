package com.primefuel.fulltank.platform.payment.domain.model.queries;

import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentStatus;

import java.time.Instant;

public record GetPaymentsByProviderIdQuery(
        Long providerId, PaymentStatus status, Instant from, Instant to) {
    public GetPaymentsByProviderIdQuery {
        if (providerId == null || providerId <= 0) {
            throw new IllegalArgumentException("Provider id must be positive");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
    }
}
