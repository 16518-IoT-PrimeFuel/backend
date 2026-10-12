package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.primefuel.fulltank.platform.RucFixtures;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

/**
 * Real flow of a freshly registered distributor: sign-up, sign-in and bearer token, with no mocked
 * principal or membership access. A provider without data must read empty lists, not 500.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:fresh_provider_reads;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class FreshProviderAccountReadsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean JavaMailSender mailSender;

    @Test
    void aFreshDistributorReadsEmptyBuyerAndPaymentLists() throws Exception {
        String username = "fresh-provider@example.test";
        var created = mvc.perform(post("/api/authentication/sign-up").contentType("application/json")
                        .content("""
                                {"username":"%s","password":"StrongPass1!","roles":["ROLE_PROVIDER"],
                                 "providerCompany":{"name":"Fresh Provider SAC","ruc":"%s",
                                 "address":"Lima","phone":"999111223","fuelTypesOffered":["DIESEL"]}}
                                """.formatted(username, RucFixtures.withCheckDigit("2099900001"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long providerId = json.readTree(created).get("providerId").asLong();
        var signIn = mvc.perform(post("/api/authentication/sign-in").contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"StrongPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + json.readTree(signIn).get("token").asString();

        mvc.perform(get("/api/provider/buyer-companies").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/payments/provider/{id}", providerId).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
