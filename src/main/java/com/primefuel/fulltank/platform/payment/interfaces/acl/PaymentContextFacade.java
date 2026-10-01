package com.primefuel.fulltank.platform.payment.interfaces.acl;

import com.primefuel.fulltank.platform.payment.application.queryservices.PaymentQueryService;
import com.primefuel.fulltank.platform.payment.domain.model.aggregates.Payment;
import com.primefuel.fulltank.platform.payment.domain.model.queries.GetAllPaymentsQuery;
import com.primefuel.fulltank.platform.payment.domain.model.queries.GetPaymentsByCompanyIdQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Superficie pública de payment para otros contextos: datos de pago en tipos primitivos.
 */
@Service
public class PaymentContextFacade {

    /** Pago reducido; status es el nombre del estado (p. ej. "COMPLETED"). */
    public record PaymentSummary(Long orderId, String status, Double amount, LocalDateTime paidAt) {
    }

    private final PaymentQueryService paymentQueryService;

    public PaymentContextFacade(PaymentQueryService paymentQueryService) {
        this.paymentQueryService = paymentQueryService;
    }

    public List<PaymentSummary> fetchAllPayments() {
        return toSummaries(paymentQueryService.handle(new GetAllPaymentsQuery()));
    }

    public List<PaymentSummary> fetchPaymentsByCompanyId(Long companyId) {
        return toSummaries(paymentQueryService.handle(new GetPaymentsByCompanyIdQuery(companyId)));
    }

    private static List<PaymentSummary> toSummaries(List<Payment> payments) {
        return payments.stream()
                .map(payment -> new PaymentSummary(payment.getOrderId(),
                        payment.getStatus() != null ? payment.getStatus().name() : null,
                        payment.getAmount(), payment.getPaidAt()))
                .toList();
    }
}
