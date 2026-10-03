package com.primefuel.fulltank.platform.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceBindingRepository;
import com.primefuel.fulltank.platform.inventory.domain.model.aggregates.FuelProduct;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillPolicy;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConfigureRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.RefillPolicyRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;

class ProviderTankUpdateTest extends ProviderReadTestSupport {
    @Autowired FuelProductRepository products;
    @Autowired DeviceBindingRepository devices;
    @Autowired RefillPolicyRepository policies;

    @Test
    void editsThresholdProductAndDevicePreservesHistoryAndDeniesUnlinkedProvider()
            throws Exception {
        var f = fixture(true, false);
        var product =
                products.save(
                        new FuelProduct(
                                new CreateFuelProductCommand(
                                        "Gasoline",
                                        FuelType.GASOLINE,
                                        2.0,
                                        "LITRE",
                                        1000.0,
                                        2000.0,
                                        f.provider(),
                                        true)));
        policies.save(
                new RefillPolicy(
                        new ConfigureRefillPolicyCommand(
                                f.tank(),
                                f.org(),
                                20.0,
                                10.0,
                                100.0,
                                f.provider(),
                                product.getId(),
                                true)));
        var previous =
                devices.save(
                        new DeviceBinding(
                                f.org(),
                                "previous-" + f.tank(),
                                "level",
                                f.tank(),
                                Instant.parse("2026-01-01T00:00:00Z")));
        String body =
                """
                {"fuelProductId":%d,"lowLevelPercent":25,"deviceId":"replacement-%d","channel":"level"}
                """
                        .formatted(product.getId(), f.tank());
        mvc.perform(
                        put("/api/provider/tanks/{id}", f.tank())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowLevelPercent").value(25))
                .andExpect(jsonPath("$.fuelType").value("GASOLINE"))
                .andExpect(jsonPath("$.devices[0].deviceId").value("replacement-" + f.tank()));
        assertThat(devices.findById(previous.getId()).orElseThrow().isOpen()).isFalse();
        mvc.perform(
                        put("/api/provider/tanks/{id}", f.tank())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(provider(f.provider() + 50000)))
                .andExpect(status().isNotFound());
        assertThat(policies.findByTankId(f.tank()).orElseThrow().getLowLevelPercent())
                .isEqualTo(25);
        mvc.perform(
                        put("/api/provider/tanks/{id}", f.tank())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"deviceId\":\"no-channel\"}")
                                .with(provider(f.provider())))
                .andExpect(status().isBadRequest());
    }
}
