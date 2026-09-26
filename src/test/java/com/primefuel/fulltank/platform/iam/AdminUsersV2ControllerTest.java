package com.primefuel.fulltank.platform.iam;

import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.Roles;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
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
class AdminUsersV2ControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @MockitoBean JavaMailSender mailSender;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Test
    void promotionIsAdminOnlyIdempotentAndEnablesExistingAdminEndpoint() throws Exception {
        long target = signUp("promote-target");
        var nonAdmin = auth(800L, "ROLE_BUYER");
        mockMvc.perform(post("/api/v2/admin/users/{id}/promote", target).with(nonAdmin))
                .andExpect(status().isForbidden());

        var adminAuth = auth(801L, "ROLE_ADMIN");
        mockMvc.perform(post("/api/v2/admin/users/{id}/promote", target).with(adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(target))
                .andExpect(jsonPath("$.roles").isArray());
        mockMvc.perform(post("/api/v2/admin/users/{id}/promote", target).with(adminAuth))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users").with(adminAuth)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/admin/users/{id}/promote", -1L).with(adminAuth))
                .andExpect(status().isNotFound());

        var promoted = users.findById(target).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1, promoted.getRoles().stream()
                .filter(role -> role.getName() == Roles.ROLE_ADMIN).count());
        org.junit.jupiter.api.Assertions.assertTrue(promoted.getRoles().stream()
                .anyMatch(role -> role.getName() == Roles.ROLE_BUYER));
    }

    private long signUp(String prefix) throws Exception {
        int id = SEQUENCE.incrementAndGet();
        String username = prefix + id + "@example.test";
        var response = mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"StrongPass1!","roles":["ROLE_BUYER"],
                                 "buyerCompany":{"name":"Admin Test LLC","ruc":"%s","sector":"Fuel",
                                 "address":"Lima","contactEmail":"%s","phone":"999111222"}}
                                """.formatted(username, "219%08d".formatted(id), username)))
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
