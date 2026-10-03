package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class ProviderBuyerCompaniesTest extends ProviderReadTestSupport {
    @Test
    void listsOrderAndRequestRelationshipsAndNeverAnotherProvidersBuyers() throws Exception {
        var order = fixture(true, false);
        var request = fixture(false, true);
        mvc.perform(get("/api/provider/buyer-companies").with(provider(order.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(order.company()))
                .andExpect(jsonPath("$[0].tankCount").value(1))
                .andExpect(jsonPath("$[0].criticalTankCount").value(1))
                .andExpect(jsonPath("$[0].activeOrderCount").value(1))
                .andExpect(jsonPath("$[0].historicalOrderCount").value(1));
        mvc.perform(get("/api/provider/buyer-companies").with(provider(request.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(request.company()))
                .andExpect(jsonPath("$[0].historicalOrderCount").value(0));
        mvc.perform(get("/api/provider/buyer-companies").with(provider(999999)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(get("/api/provider/buyer-companies").with(auth(order.provider(), "ROLE_BUYER")))
                .andExpect(status().isForbidden());
    }
}
