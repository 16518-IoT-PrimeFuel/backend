package com.primefuel.fulltank.platform.payment.domain.repositories;

import com.primefuel.fulltank.platform.payment.domain.model.aggregates.Payment;
import com.primefuel.fulltank.platform.payment.domain.model.queries.GetPaymentsByProviderIdQuery;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.ProviderPayment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    List<ProviderPayment> findByProvider(GetPaymentsByProviderIdQuery query);

    Optional<Payment> findById(Long id);
    Optional<Payment> findByOrderId(Long orderId);
    List<Payment> findAll();
    List<Payment> findByCompanyId(Long companyId);
    Payment save(Payment payment);
}
