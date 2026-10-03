package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.FleetReservation;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.ReserveFleetCommand;
import com.primefuel.fulltank.platform.fleet.domain.repositories.FleetReservationRepository;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

class ProviderDeliveriesTest extends ProviderReadTestSupport {
    @Autowired DeliveryRepository deliveries;
    @Autowired FleetRegistry fleet;
    @Autowired FleetReservationRepository reservations;

    @Test
    void listsOnlyOwnDeliveriesAndRejectsForeignProviderAndInvalidFilters() throws Exception {
        var f = fixture(true, true);
        var order = orders.findByProviderId(f.provider()).getFirst();
        var request = requests.findByProviderId(f.provider()).getFirst();
        request.accept(order.getId());
        requests.save(request);
        var driver =
                fleet.registerDriver(
                                new RegisterDriverCommand(
                                        f.provider(),
                                        null,
                                        "Ana",
                                        "Diaz",
                                        "L-" + f.provider(),
                                        "999999999",
                                        "driver@example.test",
                                        "AVAILABLE"))
                        .getOrElse(null);
        var tanker =
                fleet.registerTanker(
                                new RegisterTankerCommand(
                                        f.provider(),
                                        "PL-" + f.provider(),
                                        "Brand",
                                        "Model",
                                        2000.0,
                                        "LITRE",
                                        "AVAILABLE"))
                        .getOrElse(null);
        var delivery =
                new Delivery(
                        new CreateDeliveryCommand(
                                order.getId(),
                                f.provider(),
                                driver.id(),
                                tanker.id(),
                                "2026-10-02",
                                "test"));
        delivery.setAssignmentCommandId("delivery-" + f.provider());
        var d = deliveries.save(delivery);
        var reservation =
                new FleetReservation(
                        new ReserveFleetCommand(
                                f.provider(),
                                driver.id(),
                                tanker.id(),
                                delivery.getAssignmentCommandId(),
                                Instant.parse("2026-10-02T13:00:00Z"),
                                Instant.parse("2026-10-02T15:00:00Z"),
                                200.0,
                                "LITRE"));
        reservation.release();
        reservations.save(reservation);
        deliveries.save(
                new Delivery(
                        new CreateDeliveryCommand(
                                992L, 702L, 9001L, 9002L, "2026-10-02", "foreign")));
        deliveries.save(
                new Delivery(
                        new CreateDeliveryCommand(
                                993L, f.provider(), 9001L, 9002L, "2026-10-03", "tomorrow")));
        mvc.perform(get("/api/deliveries").param("date", "2026-10-02").with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(d.getId()))
                .andExpect(jsonPath("$[0].scheduledDate").value("2026-10-02"))
                .andExpect(jsonPath("$[0].buyerCompanyId").value(f.company()))
                .andExpect(jsonPath("$[0].siteId").value(f.site()))
                .andExpect(jsonPath("$[0].driver.firstName").value("Ana"))
                .andExpect(jsonPath("$[0].tanker.licensePlate").value("PL-" + f.provider()))
                .andExpect(jsonPath("$[0].windowStart").value("2026-10-02T13:00:00Z"))
                .andExpect(jsonPath("$[0].requestedVolume").value(200))
                .andExpect(jsonPath("$[0].unit").value("LITRE"));
        mvc.perform(
                        get("/api/deliveries")
                                .param("providerId", "" + f.provider())
                                .with(provider(702)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/deliveries").param("date", "invalid").with(provider(f.provider())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/deliveries").param("providerId", "-1").with(provider(f.provider())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/deliveries").with(auth(f.provider(), "ROLE_BUYER")))
                .andExpect(status().isForbidden());
    }
}
