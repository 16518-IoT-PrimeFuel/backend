package com.primefuel.fulltank.platform.payment.interfaces.rest.transform;

import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.ProviderPayment;
import com.primefuel.fulltank.platform.payment.interfaces.rest.resources.ProviderPaymentResource;

public final class ProviderPaymentResourceFromDomainAssembler {
    private ProviderPaymentResourceFromDomainAssembler() {}

    public static ProviderPaymentResource toResource(ProviderPayment payment) {
        return new ProviderPaymentResource(
                payment.id(),
                payment.orderId(),
                payment.buyerCompanyId(),
                payment.buyerName(),
                payment.amount(),
                payment.currency(),
                payment.status(),
                payment.paymentMethod(),
                payment.createdAt(),
                payment.updatedAt(),
                payment.paidAt());
    }
}
