package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.inventory.domain.model.aggregates.FuelProduct;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;
import com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.OrderStatus;
import com.primefuel.fulltank.platform.payment.domain.model.aggregates.Payment;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CreatePaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentMethod;
import com.primefuel.fulltank.platform.payment.domain.repositories.PaymentRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

class ProviderAnalyticsTest extends ProviderReadTestSupport {
    @Autowired FuelProductRepository products;
    @Autowired PaymentRepository payments;
    @Autowired JdbcTemplate jdbc;

    @Test
    void filtersPeriodsKeepsLifetimeRevenueCountsSalesOnceAndDeniesForeignProvider()
            throws Exception {
        var f = fixture(true, false);
        var product =
                products.save(
                        new FuelProduct(
                                new CreateFuelProductCommand(
                                        "Diesel",
                                        FuelType.DIESEL,
                                        2.0,
                                        "GALLON",
                                        1000.0,
                                        2000.0,
                                        f.provider(),
                                        true)));
        var order = orders.findByProviderId(f.provider()).getFirst();
        order.setFuelProductId(product.getId());
        order.setStatus(OrderStatus.PAID);
        order = orders.save(order);
        jdbc.update(
                "update fuel_orders set created_at=? where id=?",
                Timestamp.valueOf("2026-09-01 10:00:00"),
                order.getId());
        var p =
                new Payment(
                        new CreatePaymentCommand(
                                order.getId(), f.company(), 200.0, PaymentMethod.BANK_TRANSFER));
        p.complete("one");
        p.setPaidAt(LocalDateTime.of(2026, 9, 15, 10, 0));
        payments.save(p);
        var installment =
                new Payment(
                        new CreatePaymentCommand(
                                order.getId(), f.company(), 25.0, PaymentMethod.BANK_TRANSFER));
        installment.complete("two");
        installment.setPaidAt(LocalDateTime.of(2026, 10, 1, 10, 0));
        payments.save(installment);
        mvc.perform(get("/api/analytics/providers/{id}", f.provider()).with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.totalRevenue").value(225.0))
                .andExpect(jsonPath("$.totalFuelSoldLitres").value(378.5411784))
                .andExpect(jsonPath("$.salesTrend.length()").value(1));
        mvc.perform(
                        get("/api/analytics/providers/{id}", f.provider())
                                .param("from", "2026-09-01")
                                .param("to", "2026-09-30")
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(200))
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.salesTrend[0].date").value("2026-09-15"));
        mvc.perform(
                        get("/api/analytics/providers/{id}", f.provider())
                                .param("from", "2026-10-01")
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(0))
                .andExpect(jsonPath("$.totalRevenue").value(25))
                .andExpect(jsonPath("$.totalFuelSoldLitres").value(0));
        mvc.perform(
                        get("/api/analytics/providers/{id}", f.provider())
                                .with(provider(f.provider() + 50000)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/analytics/providers/{id}", f.provider())
                                .param("from", "2026-10-02")
                                .param("to", "2026-10-01")
                                .with(provider(f.provider())))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/analytics/providers/{id}", f.provider())
                                .param("to", "invalid")
                                .with(provider(f.provider())))
                .andExpect(status().isBadRequest());
    }
}
