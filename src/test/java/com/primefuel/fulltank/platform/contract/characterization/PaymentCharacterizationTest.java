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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T23-A: characterizes the <em>current</em> runtime behavior of the 7 v1 {@code /api/v1/payments} routes —
 * transitions, permissions and the missing guards. Evey assertion records what the system does today, not
 * what it should do; surprises are marked {@code current-behavior} and are deliberately NOT fixed here (the
 * follow-up lives in T23-B). See {@code docs/api-ledger/T23-A-payment-characterization.md}.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:payment_characterization;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class PaymentCharacterizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JavaMailSender mailSender;

    // ---- create ------------------------------------------------------------------------------

    @Test
    void createMakesAPendingPaymentAndRejectsADuplicate() throws Exception {
        var f = new Fixture("pay-create");
        long paymentId = f.createPayment(f.orderId, 50.0);

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(f.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(50.0));

        // current-behavior: one payment per order is enforced only in the application layer (findByOrderId),
        // not by a DB constraint; a duplicate request is a 409.
        mockMvc.perform(post("/api/v1/payments").with(f.buyer).contentType("application/json")
                        .content(f.paymentBody(50.0, "CASH")))
                .andExpect(status().isConflict());
    }

    @Test
    void createRejectsAnAmountThatDoesNotMatchTheOrderTotal() throws Exception {
        var f = new Fixture("pay-amount");
        mockMvc.perform(post("/api/v1/payments").with(f.buyer).contentType("application/json")
                        .content(f.paymentBody(49.99, "CASH")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsAnOrderThatDoesNotExistOrBelongsToAnotherCompany() throws Exception {
        var f = new Fixture("pay-order");
        // Unknown order id.
        mockMvc.perform(post("/api/v1/payments").with(f.buyer).contentType("application/json")
                        .content("""
                                {"orderId":999999999,"companyId":%d,"amount":50.0,"paymentMethod":"CASH"}
                                """.formatted(f.buyerCompanyId)))
                .andExpect(status().isNotFound());
        // Known order, but the caller declares the order belongs to another company -> 404 (scoping by pair).
        var other = new Fixture("pay-order-other");
        mockMvc.perform(post("/api/v1/payments").with(f.buyer).contentType("application/json")
                        .content("""
                                {"orderId":%d,"companyId":%d,"amount":50.0,"paymentMethod":"CASH"}
                                """.formatted(other.orderId, f.buyerCompanyId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRequiresTheBuyerCompanyAndCannotBeCalledByTheProvider() throws Exception {
        var f = new Fixture("pay-perm");
        // The provider does not own the buyer company in the body -> 403.
        mockMvc.perform(post("/api/v1/payments").with(f.provider).contentType("application/json")
                        .content(f.paymentBody(50.0, "CASH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithANullCompanyIdIsBadRequestAfterT23B() throws Exception {
        // T23-A finding F1 characterized this as 403 (the @PreAuthorize ownsCompany(null) ran before the
        // manual null-check). T23-B fixed it: the presence checks now run before authorization, so a null
        // companyId answers the documented 400.
        var f = new Fixture("pay-null-company");
        mockMvc.perform(post("/api/v1/payments").with(f.buyer).contentType("application/json")
                        .content("""
                                {"orderId":%d,"amount":50.0,"paymentMethod":"CASH"}
                                """.formatted(f.orderId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIsAllowedAgainstACancelledOrder() throws Exception {
        // current-behavior (finding F2 / T01-A row 68): createPayment never checks order.status, so a payment
        // can be registered for a cancelled order.
        var f = new Fixture("pay-cancelled");
        mockMvc.perform(post("/api/v1/fuel-orders/{id}/cancel", f.orderId).with(f.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        long paymentId = f.createPayment(f.orderId, 50.0);
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(f.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ---- complete ----------------------------------------------------------------------------

    @Test
    void completeMarksThePaymentCompletedAndTheOrderPaid() throws Exception {
        var f = new Fixture("pay-complete");
        long paymentId = f.createPayment(f.orderId, 50.0);

        mockMvc.perform(post("/api/v1/payments/{id}/complete", paymentId).with(f.buyer)
                        .contentType("application/json").content("{\"transactionReference\":\"ref-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.transactionReference").value("ref-1"));

        mockMvc.perform(get("/api/v1/fuel-orders/{id}", f.orderId).with(f.provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void completeIsNotAFullNoOpOnRetryAndOverwritesTheReference() throws Exception {
        // current-behavior (finding F5): no state guard and no idempotency key. Repeating complete leaves the
        // status COMPLETED (state-idempotent) but overwrites transactionReference/paidAt — so it is not a
        // strict no-op, and the order#markPaid() guard is the only thing that can reject a retry.
        var f = new Fixture("pay-complete-retry");
        long paymentId = f.createPayment(f.orderId, 50.0);
        mockMvc.perform(post("/api/v1/payments/{id}/complete", paymentId).with(f.buyer)
                        .contentType("application/json").content("{\"transactionReference\":\"first\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/{id}/complete", paymentId).with(f.buyer)
                        .contentType("application/json").content("{\"transactionReference\":\"second\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.transactionReference").value("second"));
    }

    @Test
    void completeAfterRefundIsAccepted() throws Exception {
        // current-behavior (finding F5): refund() has no guard and complete() has no guard, so a REFUNDED
        // payment can be completed again (order#markPaid only refuses a CANCELLED order).
        var f = new Fixture("pay-complete-after-refund");
        long paymentId = f.createPayment(f.orderId, 50.0);
        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId).with(f.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mockMvc.perform(post("/api/v1/payments/{id}/complete", paymentId).with(f.buyer)
                        .contentType("application/json").content("{\"transactionReference\":\"late\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void completeWithAnEmptyReferenceIsStillAccepted() throws Exception {
        // current-behavior: transactionReference is not validated (may be null/empty) even though the ADR
        // vocabulary treats it as the evidence of the manual confirmation.
        var f = new Fixture("pay-complete-empty");
        long paymentId = f.createPayment(f.orderId, 50.0);
        mockMvc.perform(post("/api/v1/payments/{id}/complete", paymentId).with(f.buyer)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void completingAPaymentWhoseOrderWasCancelledReturns500() throws Exception {
        // current-behavior (finding F3 / T01-A row 69): FuelOrder#markPaid() throws a raw
        // IllegalStateException for a CANCELLED order; GlobalExceptionHandler has no specific mapping, so it
        // falls to the generic RuntimeException handler -> 500 (not a 409).
        var f = new Fixture("pay-complete-cancelled");
        long paymentId = f.createPayment(f.orderId, 50.0);
        mockMvc.perform(post("/api/v1/fuel-orders/{id}/cancel", f.orderId).with(f.buyer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/{id}/complete", paymentId).with(f.buyer)
                        .contentType("application/json").content("{\"transactionReference\":\"ref\"}"))
                .andExpect(status().isInternalServerError());
    }

    // ---- refund ------------------------------------------------------------------------------

    @Test
    void refundAPendingPaymentSucceeds() throws Exception {
        // current-behavior (T01-A row 70): refund() has no status guard, so a never-completed PENDING payment
        // can be "refunded".
        var f = new Fixture("pay-refund-pending");
        long paymentId = f.createPayment(f.orderId, 50.0);
        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId).with(f.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void refundIsStateIdempotentButKeepsNoReasonAndLeavesTheOrderPaid() throws Exception {
        // current-behavior (finding F4): refund has no reason/date and does not revert the order.
        var f = new Fixture("pay-refund-idem");
        long paymentId = f.createPayment(f.orderId, 50.0);
        mockMvc.perform(post("/api/v1/payments/{id}/complete", paymentId).with(f.buyer)
                        .contentType("application/json").content("{\"transactionReference\":\"ref\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId).with(f.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId).with(f.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        // The order is NOT moved back from PAID even though the payment is REFUNDED.
        mockMvc.perform(get("/api/v1/fuel-orders/{id}", f.orderId).with(f.provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    // ---- permissions / reads -----------------------------------------------------------------

    @Test
    void theProviderOfTheOrderMayCompleteAndRefund() throws Exception {
        var f = new Fixture("pay-provider-ops");
        long paymentId = f.createPayment(f.orderId, 50.0);
        mockMvc.perform(post("/api/v1/payments/{id}/complete", paymentId).with(f.provider)
                        .contentType("application/json").content("{\"transactionReference\":\"prov\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId).with(f.provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void aStrangerTenantCannotCompleteRefundOrReadAPayment() throws Exception {
        var f = new Fixture("pay-stranger");
        var stranger = new Fixture("pay-stranger-b");
        long paymentId = f.createPayment(f.orderId, 50.0);

        mockMvc.perform(post("/api/v1/payments/{id}/complete", paymentId).with(stranger.buyer)
                        .contentType("application/json").content("{\"transactionReference\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId).with(stranger.buyer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(stranger.buyer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/payments/order/{orderId}", f.orderId).with(stranger.provider))
                .andExpect(status().isNotFound());
    }

    @Test
    void readsAreScopedToTheBuyerCompanyAndTheOrderProvider() throws Exception {
        var f = new Fixture("pay-reads");
        long paymentId = f.createPayment(f.orderId, 50.0);

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(f.buyer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(f.provider)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/payments/order/{orderId}", f.orderId).with(f.provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId));
        mockMvc.perform(get("/api/v1/payments/company/{companyId}", f.buyerCompanyId).with(f.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(paymentId));
    }

    @Test
    void listAllPaymentsRequiresARoleThatNoPrincipalCanHold() throws Exception {
        // current-behavior (finding F6): GET /payments requires ROLE_ADMIN, but the Role model only allows
        // ROLE_BUYER/ROLE_PROVIDER (iam Roles enum), so no principal can ever hold it -> always 403.
        var f = new Fixture("pay-admin");
        f.createPayment(f.orderId, 50.0);
        mockMvc.perform(get("/api/v1/payments").with(f.buyer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/payments").with(f.provider)).andExpect(status().isForbidden());
    }

    // ---- independence from delivery ----------------------------------------------------------

    @Test
    void aDeliveryCanBeCompletedWhileItsPaymentIsStillPending() throws Exception {
        // Invariant "a delivery can be completed although the payment is pending": the delivery machine never
        // reads payment (T14-B), so closing the physical delivery succeeds with the payment left PENDING.
        var f = new Fixture("pay-independent");
        long paymentId = f.createPayment(f.orderId, 50.0);

        var deliveryResponse = mockMvc.perform(post("/api/v1/deliveries").with(f.provider)
                        .contentType("application/json")
                        .content("""
                                {"orderId":%d,"providerId":%d,"driverId":%d,"vehicleId":%d,
                                 "scheduledDate":"2026-10-01","notes":"payment independence"}
                                """.formatted(f.orderId, f.providerId, f.driverId, f.vehicleId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long deliveryId = objectMapper.readTree(deliveryResponse).get("id").asLong();

        mockMvc.perform(post("/api/v1/deliveries/{id}/complete", deliveryId).with(f.provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // The payment is untouched by the physical close.
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(f.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ---- fixture -----------------------------------------------------------------------------

    private static final AtomicInteger RUC_SEQUENCE = new AtomicInteger();
    private static final AtomicInteger USER_ID_SEQUENCE = new AtomicInteger(70_000);
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    private static int fixtureSeq() {
        return FIXTURE_SEQUENCE.incrementAndGet();
    }

    private static String nextRuc() {
        return "239%08d".formatted(RUC_SEQUENCE.incrementAndGet());
    }

    /** A buyer company + a provider company + product/driver/vehicle + one direct order (total 50.0). */
    private final class Fixture {
        final RequestPostProcessor buyer;
        final RequestPostProcessor provider;
        final long buyerCompanyId;
        final long providerId;
        final long fuelProductId;
        final long driverId;
        final long vehicleId;
        final long orderId;

        Fixture(String label) throws Exception {
            this.buyerCompanyId = signUpBuyer(label + "-buyer@example.test");
            this.providerId = signUpProvider(label + "-provider@example.test");
            this.buyer = authFor(buyerCompanyId, null, "ROLE_BUYER");
            this.provider = authFor(null, providerId, "ROLE_PROVIDER");
            this.fuelProductId = createFuelProduct();
            this.driverId = createDriver();
            this.vehicleId = createVehicle();
            this.orderId = createOrder();
        }

        String paymentBody(double amount, String method) {
            return """
                    {"orderId":%d,"companyId":%d,"amount":%s,"paymentMethod":"%s"}
                    """.formatted(orderId, buyerCompanyId, amount, method);
        }

        long createPayment(long order, double amount) throws Exception {
            var response = mockMvc.perform(post("/api/v1/payments").with(buyer).contentType("application/json")
                            .content("""
                                    {"orderId":%d,"companyId":%d,"amount":%s,"paymentMethod":"CASH"}
                                    """.formatted(order, buyerCompanyId, amount)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        private long createOrder() throws Exception {
            var response = mockMvc.perform(post("/api/v1/fuel-orders").with(buyer)
                            .contentType("application/json")
                            .content("""
                                    {"companyId":%d,"providerId":%d,"fuelProductId":%d,
                                     "requestedQuantity":5,"deliveryAddress":"Av. Payment 1",
                                     "scheduledDate":"2026-10-15"}
                                    """.formatted(buyerCompanyId, providerId, fuelProductId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        private long createFuelProduct() throws Exception {
            var response = mockMvc.perform(post("/api/v1/fuel-products").with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"name":"Payment Diesel","fuelType":"DIESEL","pricePerUnit":10.0,"unit":"GALLONS",
                                     "availableStock":500,"capacity":1000,"providerId":%d,"active":true}
                                    """.formatted(providerId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        private long createDriver() throws Exception {
            var response = mockMvc.perform(post("/api/v1/drivers").with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"providerId":%d,"firstName":"Payment","lastName":"Driver",
                                     "licenseNumber":"L-PAY-%d","phoneNumber":"999000555","email":"pay-driver-%d@example.test",
                                     "status":"AVAILABLE"}
                                    """.formatted(providerId, fixtureSeq(), fixtureSeq())))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        private long createVehicle() throws Exception {
            var response = mockMvc.perform(post("/api/v1/vehicles").with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"providerId":%d,"licensePlate":"PAY-V%d","brand":"Volvo","model":"FH",
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
                                 "buyerCompany":{"name":"Payment Buyer LLC","ruc":"%s","sector":"Fuel",
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
                                 "providerCompany":{"name":"Payment Provider SAC","ruc":"%s",
                                 "address":"Lima","phone":"999111223","fuelTypesOffered":["DIESEL"]}}
                                """.formatted(username, nextRuc())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("providerId").asLong();
    }

    private static RequestPostProcessor authFor(Long companyId, Long providerId, String role) {
        long userId = USER_ID_SEQUENCE.incrementAndGet();
        var principal = new UserDetailsImpl(userId, "pay-user-" + userId, "encoded", companyId, providerId,
                List.of(new SimpleGrantedAuthority(role)));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
