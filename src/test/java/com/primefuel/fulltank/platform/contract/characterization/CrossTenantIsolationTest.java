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
 * T01-B: tenant A / tenant B fixtures reproducing cross-tenant ID reuse across fuel-requests,
 * fuel-orders, deliveries, payments and equipment, exactly as the runtime behaves today.
 * Correctly-scoped endpoints are asserted as such (404/403) so future regressions are caught;
 * any real leak found is marked {@code known-gap CRITICAL} and left unfixed here — see
 * docs/api-ledger/T01-B-state-security-characterization.md, mapped to Risk Register R01/R02.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:characterization_cross_tenant;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class CrossTenantIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void tenantBCannotReadTenantAsFuelRequestOrFuelOrderOrDeliveryOrPayment() throws Exception {
        var a = new Tenant("xtenant-a");
        var b = new Tenant("xtenant-b");

        long requestId = a.createFuelRequest();
        var acceptResponse = mockMvc.perform(post("/api/v1/fuel-requests/{id}/accept", requestId).with(a.provider))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode order = objectMapper.readTree(acceptResponse);
        long orderId = order.get("id").asLong();
        long deliveryId = a.createDelivery(orderId);
        long paymentId = a.createPayment(orderId, order.get("totalPrice").asDouble());

        // Correctly scoped today: tenant B's buyer/provider principals get 404, not the data.
        mockMvc.perform(get("/api/v1/fuel-requests/{id}", requestId).with(b.buyer)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/fuel-requests/{id}", requestId).with(b.provider)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/fuel-orders/{id}", orderId).with(b.buyer)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/deliveries/{id}", deliveryId).with(b.buyer)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/deliveries/{id}", deliveryId).with(b.provider)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId).with(b.provider)).andExpect(status().isNotFound());
    }

    @Test
    void tenantBCannotReadTenantAsEquipment() throws Exception {
        var a = new Tenant("xtenant-eq-a");
        var b = new Tenant("xtenant-eq-b");
        long equipmentId = a.createEquipment(500.0, 100.0);

        // Correctly scoped today: EquipmentController filters by ownsCompany() on the read path.
        mockMvc.perform(get("/api/v1/equipment/{id}", equipmentId).with(b.buyer)).andExpect(status().isNotFound());
    }

    @Test
    void directOrderCreationRejectsAProviderIdThatDoesNotOwnTheChosenFuelProduct() throws Exception {
        // Regression test for the R01 hotfix in FuelOrderCommandServiceImpl#handle(CreateFuelOrderCommand):
        // POST /api/v1/fuel-orders used to only check @currentUserAccess.ownsCompany(resource.companyId())
        // and that fuelProductId exists, never that resource.providerId() actually owns the product
        // (unlike FuelRequestService#create, which always did that check). Now it must be rejected
        // with 403 instead of pairing tenant A's fuel product with tenant B's providerId.
        var productOwner = new Tenant("xorder-product-owner");
        var strangerProvider = new Tenant("xorder-stranger-provider");
        var buyer = new Tenant("xorder-buyer");

        mockMvc.perform(post("/api/v1/fuel-orders")
                        .with(buyer.buyer)
                        .contentType("application/json")
                        .content("""
                                {"companyId":%d,"providerId":%d,"fuelProductId":%d,
                                 "requestedQuantity":5,"deliveryAddress":"Av. Cross 1",
                                 "scheduledDate":"2026-10-15"}
                                """.formatted(buyer.buyerCompanyId, strangerProvider.providerId, productOwner.fuelProductId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/fuel-orders/provider/{id}", strangerProvider.providerId).with(strangerProvider.provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void directOrderCreationRejectsAnEquipmentIdThatDoesNotBelongToTheBuyerCompany() throws Exception {
        // Regression test for the R01 hotfix: CreateFuelOrderCommand used to carry an equipmentId
        // that was never validated against companyId, neither in
        // FuelOrderCommandServiceImpl#handle(CreateFuelOrderCommand) nor in
        // DeliveryCommandServiceImpl#handle(CompleteDeliveryCommand) (which just did
        // equipmentRepository.findById(order.getEquipmentId()) and called receiveFuel()). Tenant A
        // could place a direct order referencing tenant B's equipmentId and, once completed, mutate
        // tenant B's tank level with fuel B never ordered. Maps to Risk Register R01 (CRITICAL) —
        // this was a cross-tenant WRITE, not just a read leak. Order creation must now be rejected.
        var victim = new Tenant("xwrite-victim");
        long victimEquipmentId = victim.createEquipment(1000.0, 200.0);

        var attacker = new Tenant("xwrite-attacker");
        mockMvc.perform(post("/api/v1/fuel-orders")
                        .with(attacker.buyer)
                        .contentType("application/json")
                        .content("""
                                {"companyId":%d,"providerId":%d,"fuelProductId":%d,"equipmentId":%d,
                                 "requestedQuantity":300.0,"deliveryAddress":"Av. Xtenant 2",
                                 "scheduledDate":"2026-10-15"}
                                """.formatted(attacker.buyerCompanyId, attacker.providerId, attacker.fuelProductId, victimEquipmentId)))
                .andExpect(status().isForbidden());

        // Tenant B's tank level is untouched.
        mockMvc.perform(get("/api/v1/equipment/{id}", victimEquipmentId).with(victim.buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentLevel").value(200.0));
    }

    // ---- fixture -------------------------------------------------------------------------------

    private static final AtomicInteger RUC_SEQUENCE = new AtomicInteger();

    private static String nextRuc() {
        return "239%08d".formatted(RUC_SEQUENCE.incrementAndGet());
    }

    private static final AtomicInteger USER_ID_SEQUENCE = new AtomicInteger(50_000);
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    private static int fixtureSeq() {
        return FIXTURE_SEQUENCE.incrementAndGet();
    }

    /** One buyer company + one provider company, each with a fuel product, driver and vehicle. */
    private final class Tenant {
        final RequestPostProcessor buyer;
        final RequestPostProcessor provider;
        final long buyerCompanyId;
        final long providerId;
        final long fuelProductId;

        final String label;

        Tenant(String label) throws Exception {
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
                                     "unit":"GALLONS","deliveryAddress":"Av. Xtenant 1","deliveryDate":"2026-10-15",
                                     "source":"MANUAL"}
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
                                     "scheduledDate":"2026-10-01","notes":"xtenant fixture"}
                                    """.formatted(orderId, providerId, driverId, vehicleId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        long createPayment(long orderId, double totalPrice) throws Exception {
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

        long createEquipment(double tankCapacity, double currentLevel) throws Exception {
            var response = mockMvc.perform(post("/api/v1/equipment")
                            .with(buyer)
                            .contentType("application/json")
                            .content("""
                                    {"name":"Xtenant Tank","equipmentType":"GENERATOR","licensePlate":"XT-1",
                                     "fuelType":"DIESEL","tankCapacity":%s,"currentLevel":%s,"location":"Site A",
                                     "status":"ACTIVE","autoRefill":false,"refillThreshold":10,
                                     "companyId":%d}
                                    """.formatted(tankCapacity, currentLevel, buyerCompanyId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        private long createFuelProduct() throws Exception {
            var response = mockMvc.perform(post("/api/v1/fuel-products")
                            .with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"name":"Xtenant Diesel","fuelType":"DIESEL","pricePerUnit":8.0,"unit":"GALLONS",
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
                                    {"providerId":%d,"firstName":"Xtenant","lastName":"Driver",
                                     "licenseNumber":"L-XT-%d","phoneNumber":"999000113","email":"xtenant-driver-%d@example.test",
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
                                    {"providerId":%d,"licensePlate":"XT-V%d","brand":"Volvo","model":"FH",
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
                                 "buyerCompany":{"name":"Xtenant Buyer LLC","ruc":"%s","sector":"Fuel",
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
                                 "providerCompany":{"name":"Xtenant Provider SAC","ruc":"%s",
                                 "address":"Lima","phone":"999111223","fuelTypesOffered":["DIESEL"]}}
                                """.formatted(username, nextRuc())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("providerId").asLong();
    }

    private static RequestPostProcessor authFor(Long companyId, Long providerId, String role) {
        long userId = USER_ID_SEQUENCE.incrementAndGet();
        var principal = new UserDetailsImpl(userId, "xtenant-user-" + userId, "encoded", companyId,
                providerId, List.of(new SimpleGrantedAuthority(role)));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
