package com.primefuel.fulltank.platform.contract.characterization;

import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import tools.jackson.databind.JsonNode;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T01-B: reproduces AS-IS state-transition retries/duplicates for fuel-requests, fuel-orders
 * (direct order path), deliveries and payments. Every result here is the current runtime
 * behavior, not an approval of it — surprising outcomes are marked {@code known-gap} and are
 * deliberately NOT fixed. See docs/api-ledger/T01-B-state-security-characterization.md.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:characterization_state_retry;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class StateLifecycleRetryCharacterizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void acceptingAnAlreadyAcceptedFuelRequestReturns500InsteadOf409() throws Exception {
        // known-gap (T01-A row 66): FuelRequestService#accept() throws a plain
        // IllegalStateException on a non-PENDING request; GlobalExceptionHandler has no specific
        // mapping for it, so it falls back to the generic 500 handler instead of a 409 Conflict.
        var f = new Fixture("retry-accept");
        long requestId = f.createFuelRequest();

        mockMvc.perform(post("/api/v1/fuel-requests/{id}/accept", requestId).with(f.provider))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/fuel-requests/{id}/accept", requestId).with(f.provider))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void rejectingAnAlreadyAcceptedFuelRequestReturns500InsteadOf409() throws Exception {
        // known-gap (T01-A row 67): same raw IllegalStateException path as accept-twice, but
        // triggered by reject() after the request already transitioned to APPROVED via accept().
        var f = new Fixture("retry-reject");
        long requestId = f.createFuelRequest();

        mockMvc.perform(post("/api/v1/fuel-requests/{id}/accept", requestId).with(f.provider))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/fuel-requests/{id}/reject", requestId)
                        .with(f.provider)
                        .contentType("application/json")
                        .content("{\"reason\":\"too late\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void completingAnAlreadyDeliveredDeliveryNowReturns409() throws Exception {
        // was known-gap (T01-B): the retry used to re-run FuelOrder#receive(), which guards on
        // OrderStatus.DISPATCHED, so the second attempt threw a raw IllegalStateException that fell
        // through to the generic 500 handler. T14-B routes the v1 close through the physical machine,
        // so the second attempt is rejected by the terminal COMPLETED state as a 409 *before* any
        // order/equipment side effect runs — no double volume, no duplicate journal rows.
        var f = new Fixture("retry-complete");
        long orderId = f.createDirectOrder();
        long deliveryId = f.createDelivery(orderId);

        mockMvc.perform(post("/api/v1/deliveries/{id}/complete", deliveryId).with(f.provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        mockMvc.perform(post("/api/v1/deliveries/{id}/complete", deliveryId).with(f.provider))
                .andExpect(status().isConflict());
    }

    @Test
    void refundingAPendingNeverCompletedPaymentSucceedsWithNoGuard() throws Exception {
        // known-gap (T01-A row 70): Payment#refund() has no status guard at all, so a payment
        // that was never completed (still PENDING) can be "refunded" directly.
        var f = new Fixture("retry-refund");
        long orderId = f.createDirectOrder();
        long paymentId = f.createPayment(orderId);

        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId).with(f.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void directOrderCreationBypassesTheFuelRequestNegotiationFlowEntirely() throws Exception {
        // known-gap: POST /api/v1/fuel-orders (the "direct order" route, row 56) lets a buyer
        // create a FuelOrder without ever going through a FuelRequest's PENDING->accept
        // negotiation. Both creation paths coexist and produce indistinguishable FuelOrder rows
        // (requestId is null for the direct path) — the roadmap (S10) calls this out as
        // something v2 must make impossible; T01-A/T01-B only characterize that it still works.
        var f = new Fixture("retry-direct-order");
        long orderId = f.createDirectOrder();

        mockMvc.perform(post("/api/v1/fuel-orders/{id}/confirm", orderId).with(f.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    // ---- fixture -------------------------------------------------------------------------------

    private static final AtomicInteger RUC_SEQUENCE = new AtomicInteger();

    private static String nextRuc() {
        return "229%08d".formatted(RUC_SEQUENCE.incrementAndGet());
    }

    private static final AtomicInteger USER_ID_SEQUENCE = new AtomicInteger(9_000);
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    private static int fixtureSeq() {
        return FIXTURE_SEQUENCE.incrementAndGet();
    }

    /** One buyer + one provider + one fuel product, ready to exercise a state machine. */
    private final class Fixture {
        final RequestPostProcessor buyer;
        final RequestPostProcessor provider;
        final long buyerCompanyId;
        final long providerId;
        final long fuelProductId;

        final String label;

        Fixture(String label) throws Exception {
            this.label = label;
            this.buyerCompanyId = signUpBuyer(label + "-buyer@example.test");
            this.providerId = signUpProvider(label + "-provider@example.test");
            this.buyer = authFor(buyerCompanyId, null, "ROLE_BUYER");
            this.provider = authFor(null, providerId, "ROLE_PROVIDER");
            this.fuelProductId = createFuelProduct();
        }

        long createFuelRequest() throws Exception {
            var response = mockMvc.perform(post("/api/v1/fuel-requests")
                            .with(buyer)
                            .contentType("application/json")
                            .content("""
                                    {"buyerCompanyId":%d,"providerId":%d,"fuelProductId":%d,"quantity":10,
                                     "unit":"GALLONS","deliveryAddress":"Av. Retry 1","deliveryDate":"2026-10-15",
                                     "source":"MANUAL"}
                                    """.formatted(buyerCompanyId, providerId, fuelProductId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        long createDirectOrder() throws Exception {
            var response = mockMvc.perform(post("/api/v1/fuel-orders")
                            .with(buyer)
                            .contentType("application/json")
                            .content("""
                                    {"companyId":%d,"providerId":%d,"fuelProductId":%d,
                                     "requestedQuantity":5,"deliveryAddress":"Av. Retry 2",
                                     "scheduledDate":"2026-10-15"}
                                    """.formatted(buyerCompanyId, providerId, fuelProductId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        long createDelivery(long orderId) throws Exception {
            long driverId = createDriver();
            long vehicleId = createVehicle();
            var response = mockMvc.perform(post("/api/v1/deliveries")
                            .with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"orderId":%d,"providerId":%d,"driverId":%d,"vehicleId":%d,
                                     "scheduledDate":"2026-10-01","notes":"retry fixture"}
                                    """.formatted(orderId, providerId, driverId, vehicleId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        long createPayment(long orderId) throws Exception {
            JsonNode order = objectMapper.readTree(mockMvc.perform(post("/api/v1/fuel-orders/{id}/confirm", orderId)
                            .with(buyer))
                    .andReturn().getResponse().getContentAsString());
            double totalPrice = order.get("totalPrice").asDouble();
            var response = mockMvc.perform(post("/api/v1/payments")
                            .with(buyer)
                            .contentType("application/json")
                            .content("""
                                    {"orderId":%d,"companyId":%d,"amount":%s,"paymentMethod":"CASH"}
                                    """.formatted(orderId, buyerCompanyId, totalPrice)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        private long createFuelProduct() throws Exception {
            var response = mockMvc.perform(post("/api/v1/fuel-products")
                            .with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"name":"Retry Diesel","fuelType":"DIESEL","pricePerUnit":10.0,"unit":"GALLONS",
                                     "availableStock":500,"capacity":1000,"providerId":%d,"active":true}
                                    """.formatted(providerId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        private long createDriver() throws Exception {
            var response = mockMvc.perform(post("/api/v1/drivers")
                            .with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"providerId":%d,"firstName":"Retry","lastName":"Driver",
                                     "licenseNumber":"L-RTY-%d","phoneNumber":"999000112","email":"retry-driver-%d@example.test",
                                     "status":"AVAILABLE"}
                                    """.formatted(providerId, fixtureSeq(), fixtureSeq())))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        private long createVehicle() throws Exception {
            var response = mockMvc.perform(post("/api/v1/vehicles")
                            .with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"providerId":%d,"licensePlate":"RTY-V%d","brand":"Volvo","model":"FH",
                                     "capacity":2000,"unit":"GALLONS","status":"AVAILABLE"}
                                    """.formatted(providerId, fixtureSeq())))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }
    }

    private long signUpBuyer(String username) throws Exception {
        var response = mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"StrongPass1!","roles":["ROLE_BUYER"],
                                 "buyerCompany":{"name":"Retry Buyer LLC","ruc":"%s","sector":"Fuel",
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
                                 "providerCompany":{"name":"Retry Provider SAC","ruc":"%s",
                                 "address":"Lima","phone":"999111223","fuelTypesOffered":["DIESEL"]}}
                                """.formatted(username, nextRuc())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("providerId").asLong();
    }

    private static RequestPostProcessor authFor(Long companyId, Long providerId, String role) {
        long userId = USER_ID_SEQUENCE.incrementAndGet();
        var principal = new UserDetailsImpl(userId, "characterization-user-" + userId, "encoded", companyId,
                providerId, List.of(new SimpleGrantedAuthority(role)));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
