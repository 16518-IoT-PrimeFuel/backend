package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.safety.valve.application.ValveObservationService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

class ProviderValveObservationsTest extends ProviderReadTestSupport {
    @Autowired DeliveryRepository deliveries;
    @Autowired ValveObservationService observations;

    @Test
    void exposesLogicalStateAndUnauthorizedOpenOnlyToDeliveryOwner() throws Exception {
        var f = fixture(true, false);
        var order = orders.findByProviderId(f.provider()).getFirst();
        var delivery =
                deliveries.save(
                        new Delivery(
                                new CreateDeliveryCommand(
                                        order.getId(),
                                        f.provider(),
                                        101L,
                                        102L,
                                        "2026-10-02",
                                        "valve")));
        observations.observe(delivery.getId(), f.provider(), "CLOSED", Instant.now(), null);
        observations.observe(delivery.getId(), f.provider(), "OPEN", Instant.now(), null);
        mvc.perform(
                        get("/api/deliveries/{id}/valve-observations", delivery.getId())
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].state").value("CLOSED"))
                .andExpect(jsonPath("$[1].state").value("OPEN"))
                .andExpect(jsonPath("$[1].unauthorized").value(true));
        mvc.perform(
                        get("/api/deliveries/{id}/valve-observations", delivery.getId())
                                .with(provider(f.provider() + 50000)))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/deliveries/{id}/valve-observations", delivery.getId())
                                .with(auth(f.provider(), "ROLE_BUYER")))
                .andExpect(status().isForbidden());
    }
}
