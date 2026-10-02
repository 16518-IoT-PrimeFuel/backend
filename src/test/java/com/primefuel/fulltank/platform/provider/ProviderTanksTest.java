package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceBindingRepository;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillPolicy;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConfigureRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.RefillPolicyRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

class ProviderTanksTest extends ProviderReadTestSupport {
    @Autowired DeviceBindingRepository devices;
    @Autowired RefillPolicyRepository policies;

    @Test
    void returnsLevelsPolicyAndOnlyCurrentDevicesAndRejectsUnlinkedBuyer() throws Exception {
        var f = fixture(false, true);
        var foreign = fixture(true, false);
        devices.save(
                new DeviceBinding(
                        f.org(),
                        "sensor-" + f.tank(),
                        "level",
                        f.tank(),
                        Instant.parse("2026-01-01T00:00:00Z")));
        var revoked =
                new DeviceBinding(
                        f.org(),
                        "old-" + f.tank(),
                        "level",
                        f.tank(),
                        Instant.parse("2025-01-01T00:00:00Z"));
        revoked.revoke(Instant.parse("2025-12-31T00:00:00Z"));
        devices.save(revoked);
        policies.save(
                new RefillPolicy(
                        new ConfigureRefillPolicyCommand(
                                f.tank(), f.org(), 10.0, 10.0, 100.0, f.provider(), null, false)));
        mvc.perform(
                        get("/api/provider/tanks")
                                .param("buyerCompanyId", "" + f.company())
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(f.tank()))
                .andExpect(jsonPath("$[0].capacity").value(1000))
                .andExpect(jsonPath("$[0].currentLevel").value(150))
                .andExpect(jsonPath("$[0].levelPercent").value(15))
                .andExpect(jsonPath("$[0].lowLevelPercent").value(10))
                .andExpect(jsonPath("$[0].critical").value(false))
                .andExpect(jsonPath("$[0].devices.length()").value(1))
                .andExpect(jsonPath("$[0].devices[0].deviceId").value("sensor-" + f.tank()));
        mvc.perform(
                        get("/api/provider/tanks")
                                .param("buyerCompanyId", "" + f.company())
                                .with(provider(foreign.provider())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/provider/tanks").with(provider(foreign.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(foreign.tank()));
        mvc.perform(
                        get("/api/provider/tanks")
                                .param("buyerCompanyId", "0")
                                .with(provider(f.provider())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/provider/tanks").with(auth(f.provider(), "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
    }
}
