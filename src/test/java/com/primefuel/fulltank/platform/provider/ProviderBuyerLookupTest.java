package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;

class ProviderBuyerLookupTest extends ProviderReadTestSupport {
    @Autowired ObjectMapper json;

    @Test
    void looksUpOnlyIdentityAndLinksTheReturnedIdWithoutHistory() throws Exception {
        var f = fixture(false, false);
        String ruc = buyerCompanies.findById(f.company()).orElseThrow().getRuc();
        var response = mvc.perform(get("/api/provider/buyer-companies/lookup")
                        .with(provider(f.provider())).param("ruc", ruc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyerCompanyId").value(f.company()))
                .andExpect(jsonPath("$.name").value("Buyer " + f.provider()))
                .andExpect(jsonPath("$.ruc").value(ruc))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.sites").doesNotExist())
                .andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andReturn();
        long id = json.readTree(response.getResponse().getContentAsString())
                .path("buyerCompanyId").asLong();
        mvc.perform(get("/api/provider/tanks/{id}", f.tank()).with(provider(f.provider())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/provider/buyer-companies").with(provider(f.provider()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buyerCompanyId\":" + id + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.ruc").value(ruc))
                .andExpect(jsonPath("$.sector").value("Industry"));
        mvc.perform(get("/api/provider/tanks/{id}", f.tank()).with(provider(f.provider())))
                .andExpect(status().isOk());
        mvc.perform(post("/api/provider/buyer-companies").with(provider(f.provider()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buyerCompanyId\":" + id + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROVIDERBUYERLINK_CONFLICT"));
        mvc.perform(post("/api/provider/buyer-companies").with(provider(f.provider()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Duplicado\",\"ruc\":\"" + ruc + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUYERCOMPANY_CONFLICT"));
        mvc.perform(get("/api/provider/buyer-companies").with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruc").value(ruc))
                .andExpect(jsonPath("$[0].sector").value("Industry"));
    }

    @Test
    void anotherProviderMayDiscoverIdentityButCannotReadTheBuyersAssets() throws Exception {
        var f = fixture(true, false);
        long other = f.provider() + 50000;
        String ruc = buyerCompanies.findById(f.company()).orElseThrow().getRuc();
        mvc.perform(get("/api/provider/buyer-companies/lookup").with(provider(other)).param("ruc", ruc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyerCompanyId").value(f.company()));
        mvc.perform(get("/api/provider/tanks/{id}", f.tank()).with(provider(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/provider/buyer-companies").with(provider(other)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        for (String role : new String[] {"ROLE_BUYER", "ROLE_ADMIN"}) {
            mvc.perform(get("/api/provider/buyer-companies/lookup")
                            .with(auth(other, role)).param("ruc", ruc))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void returnsNotFoundAndRejectsMissingPartialAndInvalidRucs() throws Exception {
        var f = fixture(false, false);
        mvc.perform(get("/api/provider/buyer-companies/lookup")
                        .with(provider(f.provider())).param("ruc", "00000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUYERCOMPANY_NOT_FOUND"));
        for (String ruc : new String[] {"", "123", "1234567890a", "123456789012", "1234567890%"}) {
            mvc.perform(get("/api/provider/buyer-companies/lookup")
                            .with(provider(f.provider())).param("ruc", ruc))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get("/api/provider/buyer-companies/lookup").with(provider(f.provider())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void limitsExistingAndMissingRucLookupsPerProvider() throws Exception {
        var f = fixture(false, false);
        String ruc = buyerCompanies.findById(f.company()).orElseThrow().getRuc();
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/provider/buyer-companies/lookup").with(provider(f.provider()))
                            .param("ruc", i % 2 == 0 ? ruc : "00000000000"))
                    .andExpect(status().is(i % 2 == 0 ? 200 : 404));
        }
        mvc.perform(get("/api/provider/buyer-companies/lookup").with(provider(f.provider()))
                        .param("ruc", ruc))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOOKUP_RATE_LIMITED"))
                .andExpect(header().string("Retry-After", "60"));
        mvc.perform(get("/api/provider/buyer-companies/lookup").with(provider(f.provider() + 50000))
                        .param("ruc", ruc))
                .andExpect(status().isOk());
    }
}
