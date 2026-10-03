package com.primefuel.fulltank.platform.payment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.payment.infrastructure.persistence.jpa.entities.PaymentPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.sql.Timestamp;

@Repository
public interface PaymentPersistenceRepository extends JpaRepository<PaymentPersistenceEntity, Long> {
    @Query(value = """
            SELECT p.id AS id, p.order_id AS orderId, p.company_id AS buyerCompanyId,
                   b.name AS buyerName, p.amount AS amount, p.status AS status,
                   p.payment_method AS paymentMethod, p.created_at AS createdAt,
                   p.updated_at AS updatedAt, p.paid_at AS paidAt
            FROM payments p
            JOIN fuel_orders o ON o.id = p.order_id AND o.company_id = p.company_id
            JOIN buyer_companies b ON b.id = p.company_id
            WHERE o.provider_id = :providerId
              AND (:status IS NULL OR p.status = :status)
              AND (:fromDate IS NULL OR p.created_at >= :fromDate)
              AND (:toDate IS NULL OR p.created_at <= :toDate)
            ORDER BY p.created_at DESC, p.id DESC
            """, nativeQuery = true)
    List<ProviderPaymentProjection> findByProvider(
            @Param("providerId") Long providerId,
            @Param("status") String status,
            @Param("fromDate") Timestamp from,
            @Param("toDate") Timestamp to);

    Optional<PaymentPersistenceEntity> findByOrderId(Long orderId);
    List<PaymentPersistenceEntity> findByCompanyId(Long companyId);
}
