package com.primefuel.fulltank.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.shared.application.events.DurableEvent;
import com.primefuel.fulltank.platform.shared.application.events.DurableEventPublisher;
import com.primefuel.fulltank.platform.iam.interfaces.acl.TenantAccess;
import com.primefuel.fulltank.platform.inventory.application.ports.SupplyReservationStore;

import java.time.Instant;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:fulltank;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class FullTankPlatformApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DurableEventPublisher durableEventPublisher;

    @Autowired
    private TenantAccess tenantAccess;

    @Autowired
    private SupplyReservationStore supplyReservations;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void contextLoads() {
    }

    @Test
    void durableEventPublicationIsIdempotent() {
        var event = new DurableEvent("test:event:1", "TestEvent", "Test", "1", "ok", Instant.now());

        assertTrue(durableEventPublisher.publish(event));
        assertFalse(durableEventPublisher.publish(event));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where event_key = ?", Integer.class, event.eventKey()));
    }

    @Test
    void activeMembershipControlsTenantAccessWhenPresent() {
        jdbcTemplate.update("insert into organizations (created_at, updated_at, name, type, legacy_buyer_company_id, status) values (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?, ?)",
                "Membership Buyer", "BUYER", 731L, "ACTIVE");
        jdbcTemplate.update("insert into memberships (created_at, updated_at, user_id, organization_id, role, status) values (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?, ?)",
                731L, jdbcTemplate.queryForObject("select id from organizations where legacy_buyer_company_id = 731", Long.class), "MEMBER", "ACTIVE");
        var principal = new UserDetailsImpl(731L, "membership-user", "encoded", 999L, null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(token);

        assertTrue(tenantAccess.ownsCompany(731L));
        assertFalse(tenantAccess.ownsCompany(999L));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void supplyReservationIsAtomicAndIdempotent() {
        jdbcTemplate.update("insert into fuel_products (id, created_at, updated_at, name, fuel_type, price_per_unit, unit, available_stock, provider_id, active) values (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?)",
                901L, "Reservation Diesel", "DIESEL", 10.0, "L", 100.0, 501L, true);

        assertTrue(supplyReservations.reserve(801L, 901L, 40.0));
        assertTrue(supplyReservations.reserve(801L, 901L, 40.0));
        assertFalse(supplyReservations.reserve(802L, 901L, 70.0));
        assertEquals(60.0, jdbcTemplate.queryForObject(
                "select available_stock from fuel_products where id = 901", Double.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from supply_reservations where request_id = 801", Integer.class));
    }

    @Test
    void buyerCompanyEndpointRejectsOtherBusinessRoles() throws Exception {
        var provider = new UserDetailsImpl(1L, "provider", "encoded", null, 7L,
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        var token = new UsernamePasswordAuthenticationToken(
                provider, null, provider.getAuthorities());

        mockMvc.perform(get("/api/v1/buyer-companies/99")
                        .with(authentication(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void buyerTenantCannotReadAnotherBuyerCompany() throws Exception {
        var buyer = new UserDetailsImpl(1L, "buyer-a", "encoded", 31L, null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        var token = new UsernamePasswordAuthenticationToken(
                buyer, null, buyer.getAuthorities());

        mockMvc.perform(get("/api/v1/buyer-companies/32")
                        .with(authentication(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerTenantCannotReadAnotherProviderCompany() throws Exception {
        var provider = new UserDetailsImpl(2L, "provider-a", "encoded", null, 41L,
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        var token = new UsernamePasswordAuthenticationToken(
                provider, null, provider.getAuthorities());

        mockMvc.perform(get("/api/v1/provider-companies/42")
                        .with(authentication(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void runtimeV1RouteCountMatchesTheBaselineLedger() {
        var routeCount = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .filter(pattern -> pattern.startsWith("/api/v1/"))
                .count();

        org.junit.jupiter.api.Assertions.assertEquals(77, routeCount);
    }

    @Test
    void runtimeV1ContractFingerprintIsStable() throws Exception {
        var contract = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getMethodsCondition().getMethods().stream()
                        .flatMap(method -> mapping.getPatternValues().stream()
                                .filter(pattern -> pattern.startsWith("/api/v1/")
                                        && method != org.springframework.web.bind.annotation.RequestMethod.OPTIONS)
                                .map(pattern -> method.name() + " " + pattern)))
                .sorted()
                .collect(java.util.stream.Collectors.joining("\n"));
        var digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(contract.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertEquals(
                "79bd6b0a28b3aa535749263f2c5bc3d8cff0cb18d63cd031cd77d95a448434c6",
                java.util.HexFormat.of().formatHex(digest));
    }

    @Test
    void generatedOpenApiContainsLegacyAndReplenishmentContracts() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/fuel-requests']").exists())
                .andExpect(jsonPath("$.paths['/api/v2/buyer-companies/{companyId}/replenishment-requests']").exists())
                .andExpect(jsonPath("$.paths['/api/v2/provider-companies/{providerId}/replenishment-requests/{requestId}/accept']").exists())
                .andExpect(jsonPath("$.paths['/api/v2/telemetry/readings']").exists());
    }

    @Test
    void emptyDatabaseMaterializesTheDeclaredLegacyTables() {
        var expectedTables = Set.of(
                "USERS", "ROLES", "BUYER_COMPANIES", "PROVIDER_COMPANIES", "PASSWORD_RESET_TOKENS",
                "FUEL_PRODUCTS", "EQUIPMENT", "FUEL_REQUESTS", "FUEL_ORDERS", "PAYMENTS",
                "DRIVERS", "VEHICLES", "DELIVERIES", "NOTIFICATIONS", "PROVIDER_RATINGS",
                "USER_ROLES", "PROVIDER_COMPANY_FUEL_TYPES");
        var actualTables = jdbcTemplate.execute((ConnectionCallback<Set<String>>) connection -> {
            var tables = new HashSet<String>();
            try (var result = connection.getMetaData().getTables(null, "PUBLIC", "%", new String[] {"TABLE"})) {
                while (result.next()) tables.add(result.getString("TABLE_NAME"));
            }
            return tables;
        });

        org.junit.jupiter.api.Assertions.assertTrue(actualTables.containsAll(expectedTables),
                () -> "Missing legacy tables: " + expectedTables.stream()
                        .filter(table -> !actualTables.contains(table)).toList());
    }

    @Test
    void buyerCannotCreateNotificationForAnotherUserByAddingTheirOwnCompanyId() throws Exception {
        var buyer = new UserDetailsImpl(1L, "buyer", "encoded", 31L, null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        var token = new UsernamePasswordAuthenticationToken(buyer, null, buyer.getAuthorities());

        mockMvc.perform(post("/api/v1/notifications")
                        .with(authentication(token))
                        .contentType("application/json")
                        .content("""
                                {"userId":99,"companyId":31,"providerId":null,"type":"NEW_REQUEST","title":"x","message":"x","referenceId":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicSignupCreatesAndLinksItsOwnBuyerCompany() throws Exception {
        mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType("application/json")
                        .content("""
                                {"username":"signup-owner@example.test","password":"StrongPass1!","roles":["ROLE_BUYER"],"buyerCompany":{"name":"Signup LLC","ruc":"20999111223","sector":"Fuel","address":"Lima","contactEmail":"signup-owner@example.test","phone":"999111222"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").isNumber());
    }

    @Test
    void signupCannotAttachAnExistingCompanyId() throws Exception {
        mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType("application/json")
                        .content("""
                                {"username":"claim@example.test","password":"StrongPass1!","roles":["ROLE_BUYER"],"companyId":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetIsDeliveredOnceAndChangesThePassword() throws Exception {
        mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType("application/json")
                        .content("""
                                {"username":"reset-owner@example.test","password":"OldPass123!","roles":["ROLE_BUYER"],"buyerCompany":{"name":"Reset LLC","ruc":"20999111224","sector":"Fuel","address":"Lima","contactEmail":"reset-owner@example.test","phone":"999111222"}}
                                """))
                .andExpect(status().isCreated());
        clearInvocations(mailSender);

        mockMvc.perform(post("/api/v1/authentication/password-reset/request")
                        .contentType("application/json")
                        .content("{\"email\":\"reset-owner@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "If the account exists, password reset instructions have been sent."));

        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        var match = java.util.regex.Pattern.compile("token=([a-f0-9]{64})")
                .matcher(message.getValue().getText());
        org.junit.jupiter.api.Assertions.assertTrue(match.find());
        var token = match.group(1);

        mockMvc.perform(post("/api/v1/authentication/password-reset/confirm")
                        .contentType("application/json")
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/authentication/sign-in")
                        .contentType("application/json")
                        .content("{\"username\":\"reset-owner@example.test\",\"password\":\"NewPass123!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/authentication/password-reset/confirm")
                        .contentType("application/json")
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"OtherPass123!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetRequestDoesNotRevealUnknownAccounts() throws Exception {
        mockMvc.perform(post("/api/v1/authentication/password-reset/request")
                        .contentType("application/json")
                        .content("{\"email\":\"missing@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "If the account exists, password reset instructions have been sent."));
    }

}
