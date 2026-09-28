package com.primefuel.fulltank.platform.contract;

import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.EquipmentCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.contract.ReplenishmentTestFixtures;
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
import static org.mockito.Mockito.when;
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

    @Autowired private CustomerCommandService customerCommandService;
    @Autowired private EquipmentCommandService equipmentCommandService;
    @Autowired private TankCommandService tankCommandService;
    @Autowired private com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository buyerCompanyRepository;
    @Autowired private com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationRepository organizationRepository;
    @Autowired private com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository userRepository;
    @Autowired private com.primefuel.fulltank.platform.iam.application.commandservices.OnboardingCommandService onboardingCommandService;
    @Autowired private com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory legacyCompanyDirectory;

    @MockitoBean private MembershipAccess membershipAccess;

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
        when(membershipAccess.currentOrganizationId()).thenReturn(java.util.Optional.of(buyerCompanyId));
        mockMvc.perform(get("/api/replenishment-requests/{id}", requestId).with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        var acceptResponse = mockMvc.perform(post("/api/replenishment-requests/{id}/accept", requestId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.orderId").isNumber())
                .andReturn().getResponse().getContentAsString();
        JsonNode accepted = objectMapper.readTree(acceptResponse);
        long orderId = accepted.get("orderId").asLong();
        double totalPrice = accepted.get("unitPrice").asDouble() * accepted.get("quantity").asDouble();
        assertEquals("ACCEPTED", accepted.get("status").asText());

        // fuel-orders family ------------------------------------------------------------------
        mockMvc.perform(get("/api/fuel-orders/{id}", orderId).with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(buyerCompanyId));

        // deliveries family ---------------------------------------------------------------------
        var createDeliveryResponse = mockMvc.perform(post("/api/deliveries")
                        .with(provider)
                        .contentType("application/json")
                        .content("""
                                {"commandId":"golden-%d","orderId":%d,"driverId":%d,"tankerId":%d,"windowStart":"2099-10-15T08:00:00Z","windowEnd":"2099-10-15T16:00:00Z","scheduledDate":"2026-10-01","notes":"golden path"}
                                """.formatted(orderId, orderId, driverId, vehicleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.physicalState").value("ASSIGNED"))
                .andReturn().getResponse().getContentAsString();
        long deliveryId = objectMapper.readTree(createDeliveryResponse).get("deliveryId").asLong();
        mockMvc.perform(get("/api/fuel-orders/{id}", orderId).with(provider))
                .andExpect(jsonPath("$.status").value("DISPATCHED"));

        mockMvc.perform(post("/api/deliveries/{id}/start", deliveryId).with(provider)).andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/{id}/arrive", deliveryId).with(provider)).andExpect(status().isOk());
        // The physical close applies the completion effects: the order moves to PENDING_PAYMENT.
        mockMvc.perform(post("/api/deliveries/{id}/complete", deliveryId)
                        .with(provider)
                        .contentType("application/json")
                        .content("{\"deliveredVolume\":%s}".formatted(accepted.get("quantity").asDouble())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/fuel-orders/{id}", orderId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        // The reserved stock is consumed by the delivered volume (500 - 100).
        mockMvc.perform(get("/api/fuel-products/{id}", fuelProductId).with(provider))
                .andExpect(jsonPath("$.availableStock").value(400.0));

        // payments family -----------------------------------------------------------------------
        var createPaymentResponse = mockMvc.perform(post("/api/payments")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"orderId":%d,"companyId":%d,"amount":%s,"paymentMethod":"BANK_TRANSFER"}
                                """.formatted(orderId, buyerCompanyId, totalPrice)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long paymentId = objectMapper.readTree(createPaymentResponse).get("id").asLong();

        mockMvc.perform(post("/api/payments/{id}/complete", paymentId)
                        .with(buyer)
                        .contentType("application/json")
                        .content("{\"transactionReference\":\"golden-ref-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/fuel-orders/{id}", orderId).with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/api/payments/order/{orderId}", orderId).with(provider))
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
        var acceptResponse = mockMvc.perform(post("/api/replenishment-requests/{id}/accept", requestId).with(provider))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(acceptResponse).get("orderId").asLong();

        mockMvc.perform(post("/api/fuel-orders/{id}/confirm", orderId).with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Confirming again from CONFIRMED still succeeds: no guard at all.
        mockMvc.perform(post("/api/fuel-orders/{id}/confirm", orderId).with(buyer))
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
        var acceptResponse = mockMvc.perform(post("/api/replenishment-requests/{id}/accept", requestId).with(provider))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode accepted = objectMapper.readTree(acceptResponse);
        long orderId = accepted.get("orderId").asLong();
        double totalPrice = accepted.get("unitPrice").asDouble() * accepted.get("quantity").asDouble();

        mockMvc.perform(post("/api/fuel-orders/{id}/cancel", orderId).with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/payments")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"orderId":%d,"companyId":%d,"amount":%s,"paymentMethod":"CASH"}
                                """.formatted(orderId, buyerCompanyId, totalPrice)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // A mismatched amount is still rejected regardless of order status.
        mockMvc.perform(post("/api/payments")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"orderId":%d,"companyId":%d,"amount":1.0,"paymentMethod":"CASH"}
                                """.formatted(orderId, buyerCompanyId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRequestOnAccountsAndTanksCreatedThroughTheApiCanBeAccepted() throws Exception {
        // No legacy fixture: the account has no legacyCompanyId and the tank no legacy equipment.
        long buyerCompanyId = signUpBuyer("rest-buyer@example.test");
        long providerId = signUpProvider("rest-provider@example.test");
        var buyer = authFor(104L, buyerCompanyId, null, "ROLE_BUYER");
        var provider = authFor(204L, null, providerId, "ROLE_PROVIDER");
        long fuelProductId = createFuelProduct(provider, providerId);
        var ruc = buyerCompanyRepository.findById(buyerCompanyId).orElseThrow().getRuc();
        long organizationId = organizationRepository.findByRuc(ruc).orElseThrow().getId();
        when(membershipAccess.currentOrganizationId()).thenReturn(java.util.Optional.of(organizationId));

        long customerId = idOf(mockMvc.perform(post("/api/customers").with(buyer)
                .contentType("application/json").content("{\"name\":\"Rest Buyer\"}")));
        long siteId = idOf(mockMvc.perform(post("/api/customers/{id}/sites", customerId).with(buyer)
                .contentType("application/json").content("{\"name\":\"Planta\",\"address\":\"Av. Rest 1\"}")));
        long tankId = idOf(mockMvc.perform(post("/api/tanks").with(buyer).contentType("application/json")
                .content("""
                        {"customerAccountId":%d,"siteId":%d,"name":"T1","fuelType":"DIESEL","capacity":5000,"unit":"GALLONS"}
                        """.formatted(customerId, siteId))));
        long requestId = idOf(mockMvc.perform(post("/api/replenishment-requests").with(buyer)
                .contentType("application/json").content("""
                        {"customerAccountId":%d,"tankId":%d,"providerId":%d,"fuelProductId":%d,"quantity":100,
                         "unit":"GALLONS","deliveryAddress":"Av. Rest 1","deliveryDate":"2099-10-15"}
                        """.formatted(customerId, tankId, providerId, fuelProductId))));

        var accepted = mockMvc.perform(post("/api/replenishment-requests/{id}/accept", requestId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").isNumber())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/fuel-orders/{id}", objectMapper.readTree(accepted).get("orderId").asLong())
                        .with(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(buyerCompanyId));
    }

    @Test
    void aBuyerCompanyIsNotResolvedByRucAloneForAnOrganizationItsUserDoesNotBelongTo() throws Exception {
        // A company registered publicly (no user) and an organization onboarded by someone else with its RUC.
        var ruc = nextRuc();
        mockMvc.perform(post("/api/buyer-companies").contentType("application/json").content("""
                        {"name":"Victim SAC","ruc":"%s","sector":"Fuel","address":"Lima",
                         "contactEmail":"victim@example.test","phone":"999111222"}
                        """.formatted(ruc)))
                .andExpect(status().isCreated());
        signUpBuyer("squatter@example.test");
        long squatterUserId = userRepository.findByUsername("squatter@example.test").orElseThrow().getId();
        long organizationId = onboardingCommandService.handle(new com.primefuel.fulltank.platform.iam.domain.model.commands
                .OnboardOrganizationCommand("Squatter Org", ruc, com.primefuel.fulltank.platform.iam.domain.model
                .valueobjects.OrganizationType.CUSTOMER, squatterUserId)).getOrElse(null).getId();

        assertEquals(java.util.Optional.empty(), legacyCompanyDirectory.buyerCompanyIdForOrganization(organizationId));
    }

    // ---- fixtures -----------------------------------------------------------------------------

    private long idOf(org.springframework.test.web.servlet.ResultActions created) throws Exception {
        var body = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private static final AtomicInteger RUC_SEQUENCE = new AtomicInteger();

    private static String nextRuc() {
        return "209%08d".formatted(RUC_SEQUENCE.incrementAndGet());
    }

    private long signUpBuyer(String username) throws Exception {
        var response = mockMvc.perform(post("/api/authentication/sign-up")
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
        var response = mockMvc.perform(post("/api/authentication/sign-up")
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
        var response = mockMvc.perform(post("/api/fuel-products")
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
        var response = mockMvc.perform(post("/api/drivers")
                        .with(provider)
                        .contentType("application/json")
                        .content("""
                                {"firstName":"Golden","lastName":"Driver",
                                 "licenseNumber":"L-1","phoneNumber":"999000111","email":"driver@example.test",
                                 "status":"AVAILABLE"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createVehicle(RequestPostProcessor provider, long providerId) throws Exception {
        var response = mockMvc.perform(post("/api/tankers")
                        .with(provider)
                        .contentType("application/json")
                        .content("""
                                {"licensePlate":"ABC-123","brand":"Volvo","model":"FH",
                                 "capacity":2000,"unit":"GALLONS","status":"AVAILABLE"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createFuelRequest(RequestPostProcessor buyer, long buyerCompanyId, long providerId,
                                    long fuelProductId) throws Exception {
        var fixture = ReplenishmentTestFixtures.create(customerCommandService, equipmentCommandService,
                tankCommandService, buyerCompanyId, "Av. Golden 123");
        when(membershipAccess.currentOrganizationId()).thenReturn(java.util.Optional.of(fixture.organizationId()));
        var response = mockMvc.perform(post("/api/replenishment-requests")
                        .with(buyer)
                        .contentType("application/json")
                        .content("""
                                {"customerAccountId":%d,"tankId":%d,"providerId":%d,"fuelProductId":%d,"quantity":100,
                                 "unit":"GALLONS","deliveryAddress":"Av. Golden 123","deliveryDate":"2099-10-15",
                                 "source":"MANUAL"}
                                """.formatted(fixture.customerId(), fixture.tankId(), providerId, fuelProductId)))
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
