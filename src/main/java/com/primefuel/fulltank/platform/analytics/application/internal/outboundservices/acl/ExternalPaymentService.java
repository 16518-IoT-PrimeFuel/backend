package com.primefuel.fulltank.platform.analytics.application.internal.outboundservices.acl;

import com.primefuel.fulltank.platform.payment.interfaces.acl.PaymentContextFacade;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ACL de analytics hacia payment: traduce la facade a tipos propios de analytics.
 */
@Service
public class ExternalPaymentService {

    public record PaymentData(Long orderId, String status, Double amount, LocalDateTime paidAt) {
    }

    private final PaymentContextFacade paymentContextFacade;

    public ExternalPaymentService(PaymentContextFacade paymentContextFacade) {
        this.paymentContextFacade = paymentContextFacade;
    }

    public List<PaymentData> fetchAllPayments() {
        return toData(paymentContextFacade.fetchAllPayments());
    }

    public List<PaymentData> fetchPaymentsByCompanyId(Long companyId) {
        return toData(paymentContextFacade.fetchPaymentsByCompanyId(companyId));
    }

    private static List<PaymentData> toData(List<PaymentContextFacade.PaymentSummary> payments) {
        return payments.stream()
                .map(p -> new PaymentData(p.orderId(), p.status(), p.amount(), p.paidAt()))
                .toList();
    }
}
