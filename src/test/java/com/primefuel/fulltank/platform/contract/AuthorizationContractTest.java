package com.primefuel.fulltank.platform.contract;

import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T01-A negative/authorization characterization across the auth, fuel-requests/fuel-orders,
 * deliveries and payments families. Characterizes current behavior as-is; any surprising
 * result is left as a documented {@code known-gap} in docs/api-ledger, not corrected here.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:contract_authorization;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class AuthorizationContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void protectedEndpointsRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/fuel-orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/payments")).andExpect(status().isUnauthorized());
    }

    @Test
    void signInWithWrongCredentialsRespondsWithMismatchedStatusesForBadPasswordVsUnknownUser() throws Exception {
        // known-gap: wrong password returns 400 (VALIDATION_ERROR) instead of 401, and an
        // unknown username returns 404 instead of a uniform "invalid credentials" response —
        // unlike /password-reset/request, sign-in leaks whether a username is registered.
        mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType("application/json")
                        .content("""
                                {"username":"auth-neg@example.test","password":"StrongPass1!","roles":["ROLE_BUYER"],
                                 "buyerCompany":{"name":"Auth Neg LLC","ruc":"%s","sector":"Fuel",
                                 "address":"Lima","contactEmail":"auth-neg@example.test","phone":"999111222"}}
                                """.formatted(nextRuc())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/authentication/sign-in")
                        .contentType("application/json")
                        .content("{\"username\":\"auth-neg@example.test\",\"password\":\"WrongPass1!\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/authentication/sign-in")
                        .contentType("application/json")
                        .content("{\"username\":\"never-registered@example.test\",\"password\":\"WrongPass1!\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aProviderCannotAcceptAnotherProvidersFuelRequest() throws Exception {
        long buyerCompanyId = signUpBuyer("neg-buyer@example.test");
        long ownerProviderId = signUpProvider("neg-provider-owner@example.test");
        long strangerProviderId = signUpProvider("neg-provider-stranger@example.test");
        var buyer = authFor(301L, buyerCompanyId, null, "ROLE_BUYER");
        var owner = authFor(302L, null, ownerProviderId, "ROLE_PROVIDER");
        var stranger = authFor(303L, null, strangerProviderId, "ROLE_PROVIDER");

        var productResponse = mockMvc.perform(post("/api/v1/fuel-products")
                        .with(owner)
                        .contentType("application/json")
                        .content("""
                                {"name":"Neg Diesel","fuelType":"DIESEL","pricePerUnit":10.0,"unit":"GALLONS",
                                 "availableStock":500,"capacity":1000,"providerId":%d,"active":true}
                                """.formatted(ownerProviderId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fuelProductId = objectMapper.readTree(productResponse).get("id").asLong();

        var requestResponse = mockMvc.perform(post("/api/v1/fuel-requests")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"buyerCompanyId":%d,"providerId":%d,"fuelProductId":%d,"quantity":10,
                                 "unit":"GALLONS","deliveryAddress":"Av. Neg 1","deliveryDate":"2099-10-15",
                                 "source":"MANUAL"}
                                """.formatted(buyerCompanyId, ownerProviderId, fuelProductId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long requestId = objectMapper.readTree(requestResponse).get("id").asLong();

        mockMvc.perform(post("/api/v1/fuel-requests/{id}/accept", requestId).with(stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/fuel-requests/{id}/accept", requestId).with(owner))
                .andExpect(status().isOk());
    }

    @Test
    void aBuyerCannotCreateADeliveryForAProvider() throws Exception {
        long buyerCompanyId = signUpBuyer("neg-delivery-buyer@example.test");
        long providerId = signUpProvider("neg-delivery-provider@example.test");
        var buyer = authFor(304L, buyerCompanyId, null, "ROLE_BUYER");

        mockMvc.perform(post("/api/v1/deliveries")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"orderId":1,"providerId":%d,"driverId":1,"vehicleId":1,
                                 "scheduledDate":"2026-10-01","notes":"forbidden"}
                                """.formatted(providerId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void paymentCreationRejectsAnAmountThatDoesNotMatchTheOrderTotal() throws Exception {
        long buyerCompanyId = signUpBuyer("neg-payment-buyer@example.test");
        long providerId = signUpProvider("neg-payment-provider@example.test");
        var buyer = authFor(305L, buyerCompanyId, null, "ROLE_BUYER");
        var provider = authFor(306L, null, providerId, "ROLE_PROVIDER");

        var productResponse = mockMvc.perform(post("/api/v1/fuel-products")
                        .with(provider)
                        .contentType("application/json")
                        .content("""
                                {"name":"Neg Payment Diesel","fuelType":"DIESEL","pricePerUnit":12.0,"unit":"GALLONS",
                                 "availableStock":500,"capacity":1000,"providerId":%d,"active":true}
                                """.formatted(providerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fuelProductId = objectMapper.readTree(productResponse).get("id").asLong();

        var requestResponse = mockMvc.perform(post("/api/v1/fuel-requests")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"buyerCompanyId":%d,"providerId":%d,"fuelProductId":%d,"quantity":5,
                                 "unit":"GALLONS","deliveryAddress":"Av. Neg 2","deliveryDate":"2099-10-15",
                                 "source":"MANUAL"}
                                """.formatted(buyerCompanyId, providerId, fuelProductId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long requestId = objectMapper.readTree(requestResponse).get("id").asLong();

        var acceptResponse = mockMvc.perform(post("/api/v1/fuel-requests/{id}/accept", requestId).with(provider))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(acceptResponse).get("id").asLong();

        mockMvc.perform(post("/api/v1/payments")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"orderId":%d,"companyId":%d,"amount":0.01,"paymentMethod":"CASH"}
                                """.formatted(orderId, buyerCompanyId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buyerAndProviderCompanySignupRemainPublicByDesign() throws Exception {
        // known-gap (documented in S04 of the roadmap): these two creation endpoints are the
        // only public (permitAll) POST routes outside /authentication/**. Anyone, unauthenticated,
        // can register a buyer or provider company shell without an associated user account.
        mockMvc.perform(post("/api/v1/buyer-companies")
                        .contentType("application/json")
                        .content("""
                                {"name":"Anonymous LLC","ruc":"%s","sector":"Fuel","address":"Lima",
                                 "contactEmail":"anon@example.test","phone":"999111229"}
                                """.formatted(nextRuc())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/provider-companies")
                        .contentType("application/json")
                        .content("""
                                {"name":"Anonymous Provider SAC","ruc":"%s","address":"Lima",
                                 "phone":"999111230","fuelTypesOffered":["DIESEL"]}
                                """.formatted(nextRuc())))
                .andExpect(status().isCreated());
    }

    // ---- fixtures -----------------------------------------------------------------------------

    private static final AtomicInteger RUC_SEQUENCE = new AtomicInteger();

    private static String nextRuc() {
        return "219%08d".formatted(RUC_SEQUENCE.incrementAndGet());
    }

    private long signUpBuyer(String username) throws Exception {
        var response = mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"StrongPass1!","roles":["ROLE_BUYER"],
                                 "buyerCompany":{"name":"Neg Buyer LLC","ruc":"%s","sector":"Fuel",
                                 "address":"Lima","contactEmail":"%s","phone":"999111222"}}
                                """.formatted(username, nextRuc(), username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("companyId").asLong();
    }

    private long signUpProvider(String username) throws Exception {
        var response = mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"StrongPass1!","roles":["ROLE_PROVIDER"],
                                 "providerCompany":{"name":"Neg Provider SAC","ruc":"%s",
                                 "address":"Lima","phone":"999111223","fuelTypesOffered":["DIESEL"]}}
                                """.formatted(username, nextRuc())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("providerId").asLong();
    }

    private static RequestPostProcessor authFor(Long userId, Long companyId, Long providerId, String role) {
        var principal = new UserDetailsImpl(userId, "auth-contract-user-" + userId, "encoded", companyId, providerId,
                List.of(new SimpleGrantedAuthority(role)));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
