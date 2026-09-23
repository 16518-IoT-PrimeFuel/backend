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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S22/T22-B: v1 and v2 coexist. The same delivery is created through the v1 adapter and read/operated through
 * the v2 lifecycle, without either version breaking the other.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:v1v2_coexistence;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class V1V2CoexistenceGoldenTest {

    private static final AtomicInteger RUC = new AtomicInteger();
    private static final AtomicInteger ISO = new AtomicInteger();
    private static final AtomicInteger USER = new AtomicInteger(90_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void aV1CreatedDeliveryIsReadableAndOperableThroughV2() throws Exception {
        long buyerCompanyId = signUpBuyer("coexist-buyer@example.test");
        long providerId = signUpProvider("coexist-provider@example.test");
        var buyer = authFor(buyerCompanyId, null, "ROLE_BUYER");
        var provider = authFor(null, providerId, "ROLE_PROVIDER");
        long fuelProductId = createFuelProduct(provider, providerId);
        long driverId = createDriver(provider, providerId);
        long vehicleId = createVehicle(provider, providerId);

        long orderId = objectMapper.readTree(mockMvc.perform(post("/api/v1/fuel-orders").with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"companyId":%d,"providerId":%d,"fuelProductId":%d,"requestedQuantity":5,
                                 "deliveryAddress":"Av. Coexist 1","scheduledDate":"2026-10-15"}
                                """.formatted(buyerCompanyId, providerId, fuelProductId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        // v1 create (legacy adapter).
        long deliveryId = objectMapper.readTree(mockMvc.perform(post("/api/v1/deliveries").with(provider)
                        .contentType("application/json")
                        .content("""
                                {"orderId":%d,"providerId":%d,"driverId":%d,"vehicleId":%d,
                                 "scheduledDate":"2026-10-01","notes":"coexistence"}
                                """.formatted(orderId, providerId, driverId, vehicleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        // v2 read of the same delivery: same entity, physical state derived as ASSIGNED.
        mockMvc.perform(get("/api/v2/deliveries/{id}", deliveryId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deliveryId))
                .andExpect(jsonPath("$.physicalState").value("ASSIGNED"));

        // v2 assign is idempotent over the v1-created row.
        mockMvc.perform(post("/api/v2/deliveries/{id}/assign", deliveryId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.physicalState").value("ASSIGNED"));
    }

    private long signUpBuyer(String username) throws Exception {
        var response = mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"StrongPass1!","roles":["ROLE_BUYER"],
                                 "buyerCompany":{"name":"Coexist Buyer LLC","ruc":"%s","sector":"Fuel",
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
                                 "providerCompany":{"name":"Coexist Provider SAC","ruc":"%s",
                                 "address":"Lima","phone":"999111223","fuelTypesOffered":["DIESEL"]}}
                                """.formatted(username, nextRuc())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("providerId").asLong();
    }

    private long createFuelProduct(RequestPostProcessor provider, long providerId) throws Exception {
        var response = mockMvc.perform(post("/api/v1/fuel-products").with(provider)
                        .contentType("application/json")
                        .content("""
                                {"name":"Coexist Diesel","fuelType":"DIESEL","pricePerUnit":10.0,"unit":"GALLONS",
                                 "availableStock":500,"capacity":1000,"providerId":%d,"active":true}
                                """.formatted(providerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createDriver(RequestPostProcessor provider, long providerId) throws Exception {
        var response = mockMvc.perform(post("/api/v1/drivers").with(provider)
                        .contentType("application/json")
                        .content("""
                                {"providerId":%d,"firstName":"Coexist","lastName":"Driver",
                                 "licenseNumber":"L-CO-%d","phoneNumber":"999000113","email":"co-%d@example.test",
                                 "status":"AVAILABLE"}
                                """.formatted(providerId, ISO.incrementAndGet(), ISO.incrementAndGet())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createVehicle(RequestPostProcessor provider, long providerId) throws Exception {
        var response = mockMvc.perform(post("/api/v1/vehicles").with(provider)
                        .contentType("application/json")
                        .content("""
                                {"providerId":%d,"licensePlate":"CO-V%d","brand":"Volvo","model":"FH",
                                 "capacity":2000,"unit":"GALLONS","status":"AVAILABLE"}
                                """.formatted(providerId, ISO.incrementAndGet())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private static String nextRuc() {
        return "229%08d".formatted(RUC.incrementAndGet());
    }

    private static RequestPostProcessor authFor(Long companyId, Long providerId, String role) {
        long userId = USER.incrementAndGet();
        var principal = new UserDetailsImpl(userId, "coexist-" + userId, "encoded", companyId, providerId,
                List.of(new SimpleGrantedAuthority(role)));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
