package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.ObjectMapper;

class ProviderTankDetailTest extends ProviderReadTestSupport {
    @Autowired ObjectMapper json;

    @Test
    void returnsTheLinkedTankAndHidesItFromAnotherProvider() throws Exception {
        var f = fixture(true, false);
        var detail = mvc.perform(get("/api/provider/tanks/{id}", f.tank()).with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(f.tank()))
                .andExpect(jsonPath("$.buyerCompanyId").value(f.company()))
                .andExpect(jsonPath("$.capacity").value(1000))
                .andExpect(jsonPath("$.devices").isArray())
                .andReturn().getResponse().getContentAsString();
        var list = mvc.perform(get("/api/provider/tanks").with(provider(f.provider())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(detail)).isEqualTo(json.readTree(list).get(0));
        mvc.perform(get("/api/provider/tanks/{id}", f.tank()).with(provider(f.provider() + 50000)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TANK_NOT_FOUND"));
        mvc.perform(get("/api/provider/tanks/{id}", f.tank()).with(auth(f.provider(), "ROLE_BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidIdsAndHandlesMissingTanks() throws Exception {
        mvc.perform(get("/api/provider/tanks/0").with(provider(999999)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/provider/tanks/999999999").with(provider(999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TANK_NOT_FOUND"));
    }
}
