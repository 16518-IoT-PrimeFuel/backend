package com.primefuel.fulltank.platform.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.payment.domain.model.aggregates.Payment;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CreatePaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentMethod;
import com.primefuel.fulltank.platform.payment.domain.repositories.PaymentRepository;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.ObjectMapper;

class ProviderPaymentsTest extends ProviderReadTestSupport {
    @Autowired PaymentRepository payments;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired ObjectMapper json;

    private long addPayment(Fixture f) {
        var order = orders.findByProviderId(f.provider()).getFirst();
        return payments.save(new Payment(new CreatePaymentCommand(
                order.getId(), f.company(), 200.0, PaymentMethod.BANK_TRANSFER))).getId();
    }

    @Test
    void readsOnlyOwnPaymentsInOneQueryAndRejectsAnotherProvider() throws Exception {
        var own = fixture(true, false);
        var other = fixture(true, false);
        long paymentId = addPayment(own);
        addPayment(other);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            mvc.perform(get("/api/payments/provider/{id}", own.provider()).with(provider(own.provider())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(paymentId))
                    .andExpect(jsonPath("$[0].buyerCompanyId").value(own.company()))
                    .andExpect(jsonPath("$[0].buyerName").value("Buyer " + own.provider()))
                    .andExpect(jsonPath("$[0].amount").value(200.0))
                    .andExpect(jsonPath("$[0].status").value("PENDING"))
                    .andExpect(jsonPath("$[0].paymentMethod").value("BANK_TRANSFER"))
                    .andExpect(jsonPath("$[0].createdAt").exists())
                    .andExpect(jsonPath("$[0].currency").isEmpty());
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
        mvc.perform(get("/api/payments/provider/{id}", own.provider()).with(provider(other.provider())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/payments/provider/{id}", own.provider()).with(auth(own.provider(), "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/payments/provider/{id}", own.provider()).with(auth(own.provider(), "ROLE_BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void filtersByStatusAndInclusiveCreationInstantsAndSupportsEmptyLists() throws Exception {
        var f = fixture(true, false);
        addPayment(f);
        String body = mvc.perform(get("/api/payments/provider/{id}", f.provider()).with(provider(f.provider())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String createdAt = json.readTree(body).get(0).path("createdAt").asString();
        mvc.perform(get("/api/payments/provider/{id}", f.provider()).with(provider(f.provider()))
                        .param("status", "PENDING").param("from", createdAt).param("to", createdAt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/payments/provider/{id}", f.provider()).with(provider(f.provider()))
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/payments/provider/{id}", f.provider()).with(provider(f.provider()))
                        .param("from", "2999-01-01T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/payments/provider/999999").with(provider(999999)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void returnsStructuredValidationErrors() throws Exception {
        var f = fixture(true, false);
        for (String parameter : new String[] {"status", "from", "to"}) {
            mvc.perform(get("/api/payments/provider/{id}", f.provider()).with(provider(f.provider()))
                            .param(parameter, "invalid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get("/api/payments/provider/0").with(provider(f.provider())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/payments/provider/{id}", f.provider()).with(provider(f.provider()))
                        .param("from", "2026-10-03T00:00:00Z").param("to", "2026-10-02T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
