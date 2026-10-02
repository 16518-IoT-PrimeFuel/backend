package com.primefuel.fulltank.platform.payment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.payment.domain.model.aggregates.Payment;
import com.primefuel.fulltank.platform.payment.domain.model.queries.GetPaymentsByProviderIdQuery;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.ProviderPayment;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentStatus;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentMethod;
import com.primefuel.fulltank.platform.payment.domain.repositories.PaymentRepository;
import com.primefuel.fulltank.platform.payment.infrastructure.persistence.jpa.assemblers.PaymentPersistenceAssembler;
import com.primefuel.fulltank.platform.payment.infrastructure.persistence.jpa.repositories.PaymentPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.sql.Timestamp;

@Repository
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentPersistenceRepository paymentPersistenceRepository;

    public PaymentRepositoryImpl(PaymentPersistenceRepository paymentPersistenceRepository) {
        this.paymentPersistenceRepository = paymentPersistenceRepository;
    }

    @Override
    public List<ProviderPayment> findByProvider(GetPaymentsByProviderIdQuery query) {
        return paymentPersistenceRepository.findByProvider(
                        query.providerId(),
                        query.status() == null ? null : query.status().name(),
                        query.from() == null ? null : Timestamp.from(query.from()),
                        query.to() == null ? null : Timestamp.from(query.to()))
                .stream()
                .map(row -> new ProviderPayment(
                        row.getId(),
                        row.getOrderId(),
                        row.getBuyerCompanyId(),
                        row.getBuyerName(),
                        row.getAmount(),
                        null,
                        PaymentStatus.valueOf(row.getStatus()),
                        PaymentMethod.valueOf(row.getPaymentMethod()),
                        row.getCreatedAt().toInstant(),
                        row.getUpdatedAt().toInstant(),
                        row.getPaidAt()))
                .toList();
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentPersistenceRepository.findById(id)
                .map(PaymentPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return paymentPersistenceRepository.findByOrderId(orderId)
                .map(PaymentPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<Payment> findAll() {
        return paymentPersistenceRepository.findAll().stream()
                .map(PaymentPersistenceAssembler::toDomainFromPersistence)
                .toList();
    }

    @Override
    public List<Payment> findByCompanyId(Long companyId) {
        return paymentPersistenceRepository.findByCompanyId(companyId).stream()
                .map(PaymentPersistenceAssembler::toDomainFromPersistence)
                .toList();
    }

    @Override
    public Payment save(Payment payment) {
        var entity = PaymentPersistenceAssembler.toPersistenceFromDomain(payment);
        return PaymentPersistenceAssembler.toDomainFromPersistence(
                paymentPersistenceRepository.save(entity));
    }
}
