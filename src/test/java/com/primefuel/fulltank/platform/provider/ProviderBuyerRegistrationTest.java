package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.inventory.domain.model.aggregates.FuelProduct;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;

class ProviderBuyerRegistrationTest extends ProviderReadTestSupport {
    @Autowired FuelProductRepository products;
    @Autowired ObjectMapper json;

    @Test
    void onboardsANewBuyerWithoutHistoryAndAssociatesTankButDeniesAnotherProvider()
            throws Exception {
        long providerId = 777700;
        var created =
                mvc.perform(
                                post("/api/provider/buyer-companies")
                                        .with(provider(providerId))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"name\":\"Nuevo Comprador"
                                                    + " SAC\",\"ruc\":\"20999888777\",\"sector\":\"Industrial\",\"address\":\"Planta"
                                                    + " Lima\",\"siteName\":\"Planta\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.tankCount").value(0))
                        .andExpect(jsonPath("$.historicalOrderCount").value(0))
                        .andReturn();
        var buyer = json.readTree(created.getResponse().getContentAsString());
        long company = buyer.path("id").asLong();
        long account = buyer.path("sites").get(0).path("customerAccountId").asLong();
        long site = buyer.path("sites").get(0).path("id").asLong();
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
                                        providerId,
                                        true)));
        String body =
                """
                {"buyerCompanyId":%d,"customerAccountId":%d,"siteId":%d,"name":"Nuevo tanque","fuelProductId":%d,
                 "capacity":1000,"unit":"LITRE","lowLevelPercent":20,"deviceId":"new-buyer-device","channel":"level"}
                """
                        .formatted(company, account, site, product.getId());
        var tank =
                mvc.perform(
                                post("/api/provider/tanks")
                                        .with(provider(providerId))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.buyerCompanyId").value(company))
                        .andReturn();
        long tankId = json.readTree(tank.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(
                        post("/api/provider/tanks")
                                .with(provider(providerId + 1))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/provider/tanks/{id}/readings", tankId).with(provider(providerId + 1)))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/provider/tanks")
                                .param("buyerCompanyId", String.valueOf(company))
                                .with(provider(providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(tankId));
        mvc.perform(
                        post("/api/provider/buyer-companies")
                                .with(provider(providerId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"buyerCompanyId\":" + company + "}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/api/provider/buyer-companies")
                                .with(auth(providerId, "ROLE_BUYER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"buyerCompanyId\":" + company + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void explicitlyLinksAnExistingBuyerWithoutHistory() throws Exception {
        var f = fixture(false, false);
        mvc.perform(
                        get("/api/provider/tanks")
                                .param("buyerCompanyId", String.valueOf(f.company()))
                                .with(provider(f.provider())))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/provider/buyer-companies")
                                .with(provider(f.provider()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"buyerCompanyId\":" + f.company() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.historicalOrderCount").value(0));
        mvc.perform(
                        get("/api/provider/tanks")
                                .param("buyerCompanyId", String.valueOf(f.company()))
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(f.tank()));
        mvc.perform(
                        get("/api/provider/tanks")
                                .param("buyerCompanyId", String.valueOf(f.company()))
                                .with(provider(f.provider() + 50000)))
                .andExpect(status().isNotFound());
    }
}
