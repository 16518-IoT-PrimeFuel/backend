package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ProviderValidationTest extends ProviderReadTestSupport {
    @Test
    void malformedParametersAndBodiesHaveConsistentErrorResources() throws Exception {
        var f = fixture(true, false);
        for (String path :
                new String[] {
                    "/api/deliveries?date=bad",
                    "/api/provider/tanks?buyerCompanyId=0",
                    "/api/provider/tanks/bad/readings",
                    "/api/provider/tanks/bad/refill-episodes",
                    "/api/deliveries/recommendation?orderId=0",
                    "/api/deliveries/bad/valve-observations",
                    "/api/analytics/providers/" + f.provider() + "?from=bad"
                }) {
            mvc.perform(get(path).with(provider(f.provider())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.details").isNotEmpty());
        }
        mvc.perform(
                        post("/api/provider/buyer-companies")
                                .with(provider(f.provider()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"missing RUC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(
                        get("/api/deliveries")
                                .param("providerId", String.valueOf(f.provider() + 1))
                                .with(provider(f.provider())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
