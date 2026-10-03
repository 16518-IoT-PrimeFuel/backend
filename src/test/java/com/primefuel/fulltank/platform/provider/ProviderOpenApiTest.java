package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class ProviderOpenApiTest extends ProviderReadTestSupport {
    @Test
    void documentsAllProviderOperationsAndResourceFields() throws Exception {
        mvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/deliveries'].get").exists())
                .andExpect(
                        jsonPath(
                                        "$.paths['/api/analytics/providers/{providerId}'].get.parameters[?(@.name=='from')]")
                                .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/provider/buyer-companies'].get").exists())
                .andExpect(jsonPath("$.paths['/api/provider/buyer-companies'].post").exists())
                .andExpect(jsonPath("$.paths['/api/provider/buyer-companies/lookup'].get").exists())
                .andExpect(jsonPath("$.paths['/api/payments/provider/{providerId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/provider/tanks/{tankId}'].get").exists())
                .andExpect(jsonPath("$.components.schemas.ProviderBuyerCompanyResource.properties.ruc").exists())
                .andExpect(jsonPath("$.components.schemas.ProviderBuyerCompanyResource.properties.sector").exists())
                .andExpect(jsonPath("$.components.schemas.ProviderPaymentResource.properties.currency").exists())
                .andExpect(jsonPath("$.paths['/api/provider/tanks'].get").exists())
                .andExpect(jsonPath("$.paths['/api/provider/tanks'].post").exists())
                .andExpect(jsonPath("$.paths['/api/provider/tanks/{tankId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/deliveries/recommendation'].get").exists())
                .andExpect(
                        jsonPath("$.paths['/api/provider/tanks/{tankId}/readings'].get").exists())
                .andExpect(
                        jsonPath("$.paths['/api/deliveries/{deliveryId}/valve-observations'].get")
                                .exists())
                .andExpect(
                        jsonPath("$.paths['/api/provider/tanks/{tankId}/refill-episodes'].get")
                                .exists())
                .andExpect(
                        jsonPath("$.components.schemas.ProviderTankResource.properties.devices")
                                .exists())
                .andExpect(
                        jsonPath(
                                        "$.components.schemas.ProviderAnalyticsResource.properties.totalFuelSoldLitres")
                                .exists());
    }
}
