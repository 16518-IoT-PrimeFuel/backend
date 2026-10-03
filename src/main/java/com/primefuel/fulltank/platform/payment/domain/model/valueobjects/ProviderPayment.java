package com.primefuel.fulltank.platform.payment.domain.model.valueobjects;

import java.time.Instant;
import java.time.LocalDateTime;

public record ProviderPayment(
        Long id,
        Long orderId,
        Long buyerCompanyId,
        String buyerName,
        Double amount,
        String currency,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        Instant createdAt,
        Instant updatedAt,
        LocalDateTime paidAt) {}
