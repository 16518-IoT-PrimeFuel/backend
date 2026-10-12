package com.primefuel.fulltank.platform.iam;

import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.Roles;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.AdminAuditLogRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:admin_users_v2;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class AdminUsersControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired AdminAuditLogRepository auditLog;
    @MockitoBean JavaMailSender mailSender;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Test
    void promotionIsAdminOnlyIdempotentAndEnablesExistingAdminEndpoint() throws Exception {
        long target = signUp("promote-target");
        var nonAdmin = auth(800L, "ROLE_BUYER");
        mockMvc.perform(post("/api/admin/users/{id}/promote", target).with(nonAdmin))
                .andExpect(status().isForbidden());

        var adminAuth = auth(801L, "ROLE_ADMIN");
        mockMvc.perform(post("/api/admin/users/{id}/promote", target).with(adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(target))
                .andExpect(jsonPath("$.roles").isArray());
        mockMvc.perform(post("/api/admin/users/{id}/promote", target).with(adminAuth))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users").with(adminAuth)).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/users/{id}/promote", -1L).with(adminAuth))
                .andExpect(status().isNotFound());

        var promoted = users.findById(target).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1, promoted.getRoles().stream()
                .filter(role -> role.getName() == Roles.ROLE_ADMIN).count());
        org.junit.jupiter.api.Assertions.assertTrue(promoted.getRoles().stream()
                .anyMatch(role -> role.getName() == Roles.ROLE_BUYER));
    }

    @Test
    void demoteRemovesAdminKeepsOtherRolesRefusesTheLastAdminAndIsAudited() throws Exception {
        long target = signUp("demote-target");
        var adminAuth = auth(901L, "ROLE_ADMIN");
        mockMvc.perform(post("/api/admin/users/{id}/promote", target).with(adminAuth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/users/{id}/demote", target).with(auth(902L, "ROLE_BUYER")))
                .andExpect(status().isForbidden());

        // El objetivo queda como único administrador: se retira el rol a los demás de esta base de prueba.
        for (var user : users.findAll()) {
            if (!Long.valueOf(target).equals(user.getId())
                    && user.getRoles().stream().anyMatch(role -> role.getName() == Roles.ROLE_ADMIN)) {
                users.save(user.removeRole(Roles.ROLE_ADMIN));
            }
        }
        mockMvc.perform(post("/api/admin/users/{id}/demote", target).with(adminAuth))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/users/{id}/promote", signUp("demote-second")).with(adminAuth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/users/{id}/demote", target).with(adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray());

        var demoted = users.findById(target).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(demoted.getRoles().stream()
                .noneMatch(role -> role.getName() == Roles.ROLE_ADMIN));
        org.junit.jupiter.api.Assertions.assertTrue(demoted.getRoles().stream()
                .anyMatch(role -> role.getName() == Roles.ROLE_BUYER));
        org.junit.jupiter.api.Assertions.assertTrue(auditLog.findAll().stream()
                .anyMatch(entry -> "DEMOTE".equals(entry.getAction()) && target == entry.getTargetUserId()
                        && entry.getActorUserId() == 901L));
        org.junit.jupiter.api.Assertions.assertTrue(auditLog.findAll().stream()
                .anyMatch(entry -> "PROMOTE".equals(entry.getAction()) && target == entry.getTargetUserId()));
    }

    @Test
    void demoteRevokesTheTokensAlreadyIssuedToTheDemotedAdmin() throws Exception {
        long target = signUp("demote-token");
        long other = signUp("demote-token-other");
        var adminAuth = auth(903L, "ROLE_ADMIN");
        mockMvc.perform(post("/api/admin/users/{id}/promote", target).with(adminAuth)).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/users/{id}/promote", other).with(adminAuth)).andExpect(status().isOk());

        var username = users.findById(target).orElseThrow().getUsername();
        var signIn = mockMvc.perform(post("/api/authentication/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"StrongPass1!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var bearer = "Bearer " + objectMapper.readTree(signIn).get("token").asString();
        mockMvc.perform(get("/api/fuel-orders").header("Authorization", bearer))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(
                        401, result.getResponse().getStatus()));

        Thread.sleep(1100); // iat has one-second resolution; the revocation must be strictly later
        mockMvc.perform(post("/api/admin/users/{id}/demote", target).with(adminAuth)).andExpect(status().isOk());

        mockMvc.perform(get("/api/fuel-orders").header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }

    /** RUC de 11 dígitos con dígito verificador módulo 11 (SUNAT), para que pase la validación de sign-up. */
    private static String validRuc(int id) {
        String prefix = "219%07d".formatted(id);
        int[] weights = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (prefix.charAt(i) - '0') * weights[i];
        }
        int check = 11 - sum % 11;
        return prefix + (check == 10 ? 0 : check == 11 ? 1 : check);
    }

    private long signUp(String prefix) throws Exception {
        int id = SEQUENCE.incrementAndGet();
        String username = prefix + id + "@example.test";
        var response = mockMvc.perform(post("/api/authentication/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"StrongPass1!","roles":["ROLE_BUYER"],
                                 "buyerCompany":{"name":"Admin Test LLC","ruc":"%s","sector":"Fuel",
                                 "address":"Lima","contactEmail":"%s","phone":"999111222"}}
                                """.formatted(username, validRuc(id), username)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private static RequestPostProcessor auth(Long id, String role) {
        var principal = new UserDetailsImpl(id, "admin-test-" + id, "encoded", null, null,
                List.of(new SimpleGrantedAuthority(role)));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
