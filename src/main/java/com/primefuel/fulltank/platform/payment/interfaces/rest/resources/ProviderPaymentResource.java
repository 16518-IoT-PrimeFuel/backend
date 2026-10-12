package com.primefuel.fulltank.platform.payment.interfaces.rest.resources;

import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentMethod;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ProviderPaymentResource(
        Long id,
        Long orderId,
        Long buyerCompanyId,
        String buyerName,
        Double amount,
        @Schema(description = "Moneda ISO 4217 del pago; PEN por defecto")
        String currency,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        Instant createdAt,
        Instant updatedAt,
        Instant paidAt) {}
