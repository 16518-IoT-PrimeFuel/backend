package com.primefuel.fulltank.platform.payment.domain.model.commands;

import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentMethod;

/** {@code amount} is informative only: the service charges the order's own total. */
public record CreatePaymentCommand(Long orderId, Long companyId, Double amount, PaymentMethod paymentMethod,
                                   String operationNumber) {

    public CreatePaymentCommand(Long orderId, Long companyId, Double amount, PaymentMethod paymentMethod) {
        this(orderId, companyId, amount, paymentMethod, null);
    }
}
