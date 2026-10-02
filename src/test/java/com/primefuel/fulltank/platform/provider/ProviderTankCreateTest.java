package com.primefuel.fulltank.platform.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceCredential;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceBindingRepository;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceCredentialRepository;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.services.DeviceTokenHasher;
import com.primefuel.fulltank.platform.inventory.domain.model.aggregates.FuelProduct;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

class ProviderTankCreateTest extends ProviderReadTestSupport {
    @Autowired FuelProductRepository products;
    @Autowired DeviceBindingRepository bindings;
    @Autowired DeviceCredentialRepository credentials;
    @Autowired ObjectMapper json;

    @Test
    void atomicallyAssociatesTankPolicyAndDeviceAndDeniesForeignProviderAndDuplicates()
            throws Exception {
        var f = fixture(true, false);
        var product =
                products.save(
                        new FuelProduct(
                                new CreateFuelProductCommand(
                                        "Diesel",
                                        FuelType.DIESEL,
                                        2.0,
                                        "LITRE",
                                        1000.0,
                                        2000.0,
                                        f.provider(),
                                        true)));
        String body =
                """
                {"buyerCompanyId":%d,"customerAccountId":%d,"siteId":%d,"name":"IoT tank","fuelProductId":%d,
                 "capacity":1000,"unit":"LITRE","initialLevel":250,"lowLevelPercent":20,"deviceId":"provider-create-%d","channel":"level"}
                """
                        .formatted(
                                f.company(), f.account(), f.site(), product.getId(), f.provider());
        var created =
                mvc.perform(
                                post("/api/provider/tanks")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body)
                                        .with(provider(f.provider())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.currentLevel").value(250))
                        .andExpect(jsonPath("$.fuelProductId").value(product.getId()))
                        .andExpect(
                                jsonPath("$.devices[0].deviceId")
                                        .value("provider-create-" + f.provider()))
                        .andReturn();
        long tankId = json.readTree(created.getResponse().getContentAsString()).path("id").asLong();
        long count = tanks.findByOrganizationId(f.org()).size();
        mvc.perform(
                        post("/api/provider/tanks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(provider(f.provider() + 50000)))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/provider/tanks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .with(provider(f.provider())))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/api/provider/tanks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.replace("\"level\"", "\"other-channel\""))
                                .with(provider(f.provider())))
                .andExpect(status().isConflict());
        // A closed binding whose period still overlaps rejects binding after the new tank was
        // saved.
        var overlapping =
                new DeviceBinding(
                        f.org(),
                        "rollback-" + f.provider(),
                        "level",
                        f.tank(),
                        Instant.now().minusSeconds(60));
        overlapping.revoke(Instant.now().plusSeconds(3600));
        bindings.save(overlapping);
        mvc.perform(
                        post("/api/provider/tanks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.replace("provider-create-", "rollback-"))
                                .with(provider(f.provider())))
                .andExpect(status().isConflict());
        assertThat(tanks.findByOrganizationId(f.org())).hasSize((int) count);
        // Existing authenticated ingestion resolves the supplier-created association by
        // device/channel.
        String token = "provider-create-device-test-token";
        credentials.save(
                new DeviceCredential(
                        "provider-create-" + f.provider(),
                        "level",
                        DeviceTokenHasher.hash(token),
                        1,
                        Instant.now()));
        String reading =
                """
                {"schemaVersion":1,"deviceId":"provider-create-%d","channel":"level","sequence":1,"capturedAt":"%s","level":300,"unit":"LITRE"}
                """
                        .formatted(f.provider(), Instant.now().plusSeconds(60));
        mvc.perform(
                        post("/api/telemetry/readings")
                                .header("X-Device-Token", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reading))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tankId").value(tankId))
                .andExpect(jsonPath("$.quality").value("ACCEPTED"));
    }
}
