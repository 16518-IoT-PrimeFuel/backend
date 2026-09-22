package com.primefuel.fulltank.platform.fulfillment;

import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T14-A: the v2 physical lifecycle over HTTP — the happy path, the 409 for an illegal transition and the
 * tenancy boundary (a foreign delivery is a 404, never a cross-tenant mutation).
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:delivery_rest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class DeliveriesV2ControllerTest {

    private static final AtomicLong ORDERS = new AtomicLong(9100);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @MockitoBean
    private FuelOrderQueryService fuelOrderQueryService;

    private long seedDelivery(long providerId, double requestedVolume) {
        when(fuelOrderQueryService.findRequestedQuantity(anyLong())).thenReturn(Optional.of(requestedVolume));
        var delivery = new Delivery(new CreateDeliveryCommand(
                ORDERS.incrementAndGet(), providerId, 11L, 22L, "2026-10-01", "seed"));
        delivery.dispatch();
        return deliveryRepository.save(delivery).getId();
    }

    @Test
    void walksThePhysicalMachineOverHttp() throws Exception {
        var deliveryId = seedDelivery(555L, 100.0);
        var provider = authForProvider(555L);

        mockMvc.perform(post("/api/v2/deliveries/{id}/start", deliveryId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.physicalState").value("STARTED"));
        mockMvc.perform(post("/api/v2/deliveries/{id}/arrive", deliveryId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.physicalState").value("ARRIVED"));
        mockMvc.perform(post("/api/v2/deliveries/{id}/complete", deliveryId).with(provider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveredVolume\":42.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.physicalState").value("COMPLETED"))
                .andExpect(jsonPath("$.legacyStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredVolume").value(42.5))
                .andExpect(jsonPath("$.requestedVolume").value(100.0));

        mockMvc.perform(get("/api/v2/deliveries/{id}", deliveryId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.physicalState").value("COMPLETED"));
        mockMvc.perform(get("/api/v2/deliveries/{id}/transitions", deliveryId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void rejectsAnIllegalTransitionWith409() throws Exception {
        var deliveryId = seedDelivery(556L, 100.0);
        var provider = authForProvider(556L);

        // An assigned delivery cannot arrive without starting first.
        mockMvc.perform(post("/api/v2/deliveries/{id}/arrive", deliveryId).with(provider))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsADeliveredVolumeAboveTheRequestedOneWith400() throws Exception {
        var deliveryId = seedDelivery(558L, 100.0);
        var provider = authForProvider(558L);
        mockMvc.perform(post("/api/v2/deliveries/{id}/start", deliveryId).with(provider))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v2/deliveries/{id}/complete", deliveryId).with(provider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveredVolume\":500.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aForeignTenantCannotSeeOrAdvanceADelivery() throws Exception {
        var deliveryId = seedDelivery(557L, 100.0);
        var other = authForProvider(999L);

        mockMvc.perform(get("/api/v2/deliveries/{id}", deliveryId).with(other))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v2/deliveries/{id}/start", deliveryId).with(other))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v2/deliveries/{id}/complete", deliveryId).with(other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveredVolume\":1.0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v2/deliveries/{id}/transitions", deliveryId).with(other))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor authForProvider(long providerId) {
        var principal = new UserDetailsImpl(providerId, "provider-" + providerId, "encoded", null, providerId,
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(token);
    }
}
