package com.primefuel.fulltank.platform.payment.interfaces.rest.resources;

import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentMethod;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDateTime;

public record ProviderPaymentResource(
        Long id,
        Long orderId,
        Long buyerCompanyId,
        String buyerName,
        Double amount,
        @Schema(description = "Moneda no persistida actualmente; null, nunca inferida", nullable = true)
        String currency,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        Instant createdAt,
        Instant updatedAt,
        LocalDateTime paidAt) {}
