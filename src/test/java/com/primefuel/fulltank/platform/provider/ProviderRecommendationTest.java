package com.primefuel.fulltank.platform.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.FleetReservation;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.*;
import com.primefuel.fulltank.platform.fleet.domain.repositories.FleetReservationRepository;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

class ProviderRecommendationTest extends ProviderReadTestSupport {
    @Autowired FleetRegistry fleet;
    @Autowired FleetReservationRepository reservations;
    @Autowired DeliveryRepository deliveries;

    @Test
    void recommendsSmallestSufficientUnreservedTankerWithoutAssignmentAndDeniesForeignOrder()
            throws Exception {
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
                                        "REC-" + f.provider(),
                                        "999999999",
                                        "rec@example.test",
                                        "AVAILABLE"))
                        .getOrElse(null);
        var small =
                fleet.registerTanker(
                                new RegisterTankerCommand(
                                        f.provider(),
                                        "S-" + f.provider(),
                                        "B",
                                        "M",
                                        300.0,
                                        "LITRE",
                                        "AVAILABLE"))
                        .getOrElse(null);
        var large =
                fleet.registerTanker(
                                new RegisterTankerCommand(
                                        f.provider(),
                                        "L-" + f.provider(),
                                        "B",
                                        "M",
                                        600.0,
                                        "LITRE",
                                        "AVAILABLE"))
                        .getOrElse(null);
        fleet.registerTanker(
                new RegisterTankerCommand(
                        f.provider(), "T-" + f.provider(), "B", "M", 100.0, "LITRE", "AVAILABLE"));
        mvc.perform(
                        get("/api/deliveries/recommendation")
                                .param("orderId", "" + order.getId())
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommended").value(true))
                .andExpect(jsonPath("$.tankerId").value(small.id()));
        // Same driver but small tanker blocked; another driver allows recommendation of the large
        // tanker.
        var driver2 =
                fleet.registerDriver(
                                new RegisterDriverCommand(
                                        f.provider(),
                                        null,
                                        "Luis",
                                        "Diaz",
                                        "REC2-" + f.provider(),
                                        "999999999",
                                        "rec2@example.test",
                                        "AVAILABLE"))
                        .getOrElse(null);
        reservations.save(
                new FleetReservation(
                        new ReserveFleetCommand(
                                f.provider(),
                                driver.id(),
                                small.id(),
                                "recommend-" + f.provider(),
                                Instant.parse("2026-10-02T00:00:00Z"),
                                Instant.parse("2026-10-03T00:00:00Z"),
                                200.0,
                                "LITRE")));
        mvc.perform(
                        get("/api/deliveries/recommendation")
                                .param("orderId", "" + order.getId())
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tankerId").value(large.id()))
                .andExpect(jsonPath("$.driverId").value(driver2.id()));
        mvc.perform(
                        get("/api/deliveries/recommendation")
                                .param("orderId", "" + order.getId())
                                .with(provider(f.provider() + 50000)))
                .andExpect(status().isNotFound());
        assertThat(deliveries.findByOrderId(order.getId())).isEmpty();
        assertThat(requests.findById(request.getId()).orElseThrow().isAcceptanceConsumed())
                .isFalse();
        fleet.deactivateDriver(driver.id());
        fleet.deactivateDriver(driver2.id());
        mvc.perform(
                        get("/api/deliveries/recommendation")
                                .param("orderId", "" + order.getId())
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommended").value(false))
                .andExpect(jsonPath("$.reason").value("NO_ELIGIBLE_DRIVER"));
    }
}
