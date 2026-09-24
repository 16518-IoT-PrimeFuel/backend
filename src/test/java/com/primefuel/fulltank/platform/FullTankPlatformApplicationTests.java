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

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;

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

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void contextLoads() {
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
