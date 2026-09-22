package com.primefuel.fulltank.platform.contract;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T01-A golden path: characterizes the AS-IS request -> order -> delivery -> payment flow
 * exactly as the runtime behaves today. This is a regression oracle, not an approval of the
 * behavior: known defects are called out inline as {@code known-gap} and are NOT fixed here.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:contract_golden_path;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class OrderFulfillmentGoldenPathTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void requestToPaymentFlowReproducesTheCurrentAsIsBehavior() throws Exception {
        long buyerCompanyId = signUpBuyer("golden-buyer@example.test");
        long providerId = signUpProvider("golden-provider@example.test");
        var buyer = authFor(101L, buyerCompanyId, null, "ROLE_BUYER");
        var provider = authFor(201L, null, providerId, "ROLE_PROVIDER");

        long fuelProductId = createFuelProduct(provider, providerId);
        long driverId = createDriver(provider, providerId);
        long vehicleId = createVehicle(provider, providerId);

        // fuel-requests family --------------------------------------------------------------
        long requestId = createFuelRequest(buyer, buyerCompanyId, providerId, fuelProductId);
        mockMvc.perform(get("/api/v1/fuel-requests/{id}", requestId).with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        var acceptResponse = mockMvc.perform(post("/api/v1/fuel-requests/{id}/accept", requestId).with(provider))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode order = objectMapper.readTree(acceptResponse);
        long orderId = order.get("id").asLong();
        double totalPrice = order.get("totalPrice").asDouble();
        assertEquals("PENDING", order.get("status").asText());

        // fuel-orders family ------------------------------------------------------------------
        mockMvc.perform(get("/api/v1/fuel-orders/{id}", orderId).with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(buyerCompanyId));

        // deliveries family ---------------------------------------------------------------------
        // known-gap: creating a delivery already flips it (and the order) straight to
        // DISPATCHED — the explicit POST /dispatch afterwards is a same-state no-op, not a
        // guarded transition, because Delivery#dispatch() has no state guard.
        var createDeliveryResponse = mockMvc.perform(post("/api/v1/deliveries")
                        .with(provider)
                        .contentType("application/json")
                        .content("""
                                {"orderId":%d,"providerId":%d,"driverId":%d,"vehicleId":%d,"scheduledDate":"2026-10-01","notes":"golden path"}
                                """.formatted(orderId, providerId, driverId, vehicleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andReturn().getResponse().getContentAsString();
        long deliveryId = objectMapper.readTree(createDeliveryResponse).get("id").asLong();

        mockMvc.perform(post("/api/v1/deliveries/{id}/dispatch", deliveryId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));

        mockMvc.perform(post("/api/v1/deliveries/{id}/complete", deliveryId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        mockMvc.perform(get("/api/v1/fuel-orders/{id}", orderId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        // payments family -----------------------------------------------------------------------
        var createPaymentResponse = mockMvc.perform(post("/api/v1/payments")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"orderId":%d,"companyId":%d,"amount":%s,"paymentMethod":"BANK_TRANSFER"}
                                """.formatted(orderId, buyerCompanyId, totalPrice)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long paymentId = objectMapper.readTree(createPaymentResponse).get("id").asLong();

        mockMvc.perform(post("/api/v1/payments/{id}/complete", paymentId)
                        .with(buyer)
                        .contentType("application/json")
                        .content("{\"transactionReference\":\"golden-ref-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/fuel-orders/{id}", orderId).with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/api/v1/payments/order/{orderId}", orderId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void confirmingAnOrderHasNoStateGuardAndCollidesWithTheDispatchFlow() throws Exception {
        // known-gap: FuelOrder#confirm() (unlike #dispatch()/#receive()) has no status guard,
        // so it can be called from any state including PAID/CANCELLED. It also puts the order
        // into CONFIRMED, a status the delivery flow's dispatch() guard (PENDING only) never
        // expects, effectively orphaning the order from ever being fulfilled through /deliveries.
        long buyerCompanyId = signUpBuyer("confirm-buyer@example.test");
        long providerId = signUpProvider("confirm-provider@example.test");
        var buyer = authFor(102L, buyerCompanyId, null, "ROLE_BUYER");
        var provider = authFor(202L, null, providerId, "ROLE_PROVIDER");
        long fuelProductId = createFuelProduct(provider, providerId);
        long requestId = createFuelRequest(buyer, buyerCompanyId, providerId, fuelProductId);
        var acceptResponse = mockMvc.perform(post("/api/v1/fuel-requests/{id}/accept", requestId).with(provider))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(acceptResponse).get("id").asLong();

        mockMvc.perform(post("/api/v1/fuel-orders/{id}/confirm", orderId).with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Confirming again from CONFIRMED still succeeds: no guard at all.
        mockMvc.perform(post("/api/v1/fuel-orders/{id}/confirm", orderId).with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void cancellingAnOrderDoesNotBlockCreatingAPaymentForIt() throws Exception {
        // known-gap: PaymentsController#createPayment only checks that the order exists and
        // that the amount matches its totalPrice — it never checks order.status. A CANCELLED
        // order can still receive a PENDING payment, and nothing in the payment/order flow
        // reconciles that afterwards.
        long buyerCompanyId = signUpBuyer("cancel-buyer@example.test");
        long providerId = signUpProvider("cancel-provider@example.test");
        var buyer = authFor(103L, buyerCompanyId, null, "ROLE_BUYER");
        var provider = authFor(203L, null, providerId, "ROLE_PROVIDER");
        long fuelProductId = createFuelProduct(provider, providerId);
        long requestId = createFuelRequest(buyer, buyerCompanyId, providerId, fuelProductId);
        var acceptResponse = mockMvc.perform(post("/api/v1/fuel-requests/{id}/accept", requestId).with(provider))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode order = objectMapper.readTree(acceptResponse);
        long orderId = order.get("id").asLong();
        double totalPrice = order.get("totalPrice").asDouble();

        mockMvc.perform(post("/api/v1/fuel-orders/{id}/cancel", orderId).with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/payments")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"orderId":%d,"companyId":%d,"amount":%s,"paymentMethod":"CASH"}
                                """.formatted(orderId, buyerCompanyId, totalPrice)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // A mismatched amount is still rejected regardless of order status.
        mockMvc.perform(post("/api/v1/payments")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"orderId":%d,"companyId":%d,"amount":1.0,"paymentMethod":"CASH"}
                                """.formatted(orderId, buyerCompanyId)))
                .andExpect(status().isBadRequest());
    }

    // ---- fixtures -----------------------------------------------------------------------------

    private static final AtomicInteger RUC_SEQUENCE = new AtomicInteger();

    private static String nextRuc() {
        return "209%08d".formatted(RUC_SEQUENCE.incrementAndGet());
    }

    private long signUpBuyer(String username) throws Exception {
        var response = mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"StrongPass1!","roles":["ROLE_BUYER"],
                                 "buyerCompany":{"name":"Golden Buyer LLC","ruc":"%s","sector":"Fuel",
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
                                 "providerCompany":{"name":"Golden Provider SAC","ruc":"%s",
                                 "address":"Lima","phone":"999111223","fuelTypesOffered":["DIESEL"]}}
                                """.formatted(username, nextRuc())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("providerId").asLong();
    }

    private long createFuelProduct(RequestPostProcessor provider, long providerId) throws Exception {
        var response = mockMvc.perform(post("/api/v1/fuel-products")
                        .with(provider)
                        .contentType("application/json")
                        .content("""
                                {"name":"Golden Diesel","fuelType":"DIESEL","pricePerUnit":15.5,"unit":"GALLONS",
                                 "availableStock":500,"capacity":1000,"providerId":%d,"active":true}
                                """.formatted(providerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createDriver(RequestPostProcessor provider, long providerId) throws Exception {
        var response = mockMvc.perform(post("/api/v1/drivers")
                        .with(provider)
                        .contentType("application/json")
                        .content("""
                                {"providerId":%d,"firstName":"Golden","lastName":"Driver",
                                 "licenseNumber":"L-1","phoneNumber":"999000111","email":"driver@example.test",
                                 "status":"AVAILABLE"}
                                """.formatted(providerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createVehicle(RequestPostProcessor provider, long providerId) throws Exception {
        var response = mockMvc.perform(post("/api/v1/vehicles")
                        .with(provider)
                        .contentType("application/json")
                        .content("""
                                {"providerId":%d,"licensePlate":"ABC-123","brand":"Volvo","model":"FH",
                                 "capacity":2000,"unit":"GALLONS","status":"AVAILABLE"}
                                """.formatted(providerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createFuelRequest(RequestPostProcessor buyer, long buyerCompanyId, long providerId,
                                    long fuelProductId) throws Exception {
        var response = mockMvc.perform(post("/api/v1/fuel-requests")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"buyerCompanyId":%d,"providerId":%d,"fuelProductId":%d,"quantity":100,
                                 "unit":"GALLONS","deliveryAddress":"Av. Golden 123","deliveryDate":"2026-10-15",
                                 "source":"MANUAL"}
                                """.formatted(buyerCompanyId, providerId, fuelProductId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private static RequestPostProcessor authFor(Long userId, Long companyId, Long providerId, String role) {
        var principal = new UserDetailsImpl(userId, "contract-user-" + userId, "encoded", companyId, providerId,
                List.of(new SimpleGrantedAuthority(role)));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
