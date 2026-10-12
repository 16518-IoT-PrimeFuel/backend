package com.primefuel.fulltank.platform.payment.interfaces.rest.resources;

import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentMethod;

/**
 * {@code companyId} and {@code amount} are accepted for compatibility but derived server-side from the order.
 * {@code operationNumber} (optional): bank operation number, 6-20 alphanumeric characters, unique.
 */
public record CreatePaymentResource(Long orderId, Long companyId, Double amount, PaymentMethod paymentMethod,
                                    String operationNumber) {
}
