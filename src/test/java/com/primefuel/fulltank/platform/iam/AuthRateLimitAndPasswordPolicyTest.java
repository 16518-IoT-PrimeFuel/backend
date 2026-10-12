package com.primefuel.fulltank.platform.iam;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;

/** Rate limiting on /api/authentication/**, password policy and session revocation on reset. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:auth_rate_limit;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "authorization.jwt.secret=0123456789abcdef0123456789abcdef",
            "app.rate-limit.enabled=true",
            "app.rate-limit.sign-in=3",
            "app.rate-limit.sign-up=50",
            "app.rate-limit.password-reset-request=50",
            "app.rate-limit.password-reset-confirm=50"
        })
@AutoConfigureMockMvc
class AuthRateLimitAndPasswordPolicyTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean JavaMailSender mailSender;

    private static int seq = 0;

    private String signUpBody(String username, String password, String ruc) {
        return """
                {"username":"%s","password":"%s","roles":["ROLE_BUYER"],
                 "buyerCompany":{"name":"Rate LLC","ruc":"%s","sector":"Fuel","address":"Lima",
                 "contactEmail":"%s","phone":"999111222"}}
                """.formatted(username, password, ruc, username);
    }

    private void signUp(String username, String password) throws Exception {
        mvc.perform(post("/api/authentication/sign-up").contentType("application/json")
                        .content(signUpBody(username, password, "2077700%04d".formatted(++seq))))
                .andExpect(status().isCreated());
    }

    @Test
    void signInIsRateLimitedPerIpAndUsernameWithRetryAfter() throws Exception {
        String body = "{\"username\":\"limited@example.test\",\"password\":\"WrongPass1!\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/authentication/sign-in").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/authentication/sign-in").contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // another username from the same address is a different bucket
        mvc.perform(post("/api/authentication/sign-in").contentType("application/json")
                        .content("{\"username\":\"other@example.test\",\"password\":\"WrongPass1!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weakPasswordsAreRejectedOnSignUp() throws Exception {
        for (String weak : new String[] {"onlyletters", "12345678", "Ab1"}) {
            mvc.perform(post("/api/authentication/sign-up").contentType("application/json")
                            .content(signUpBody("weak@example.test", weak, "2077700%04d".formatted(++seq))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void passwordResetRejectsWeakPasswordsAndRevokesExistingSessions() throws Exception {
        signUp("revoked@example.test", "OldPass123!");
        var signIn = mvc.perform(post("/api/authentication/sign-in").contentType("application/json")
                        .content("{\"username\":\"revoked@example.test\",\"password\":\"OldPass123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var bearer = "Bearer " + json.readTree(signIn).get("token").asString();
        mvc.perform(get("/api/fuel-orders").header("Authorization", bearer))
                .andExpect(status().isForbidden()); // authenticated (role-gated), not 401

        mvc.perform(post("/api/authentication/password-reset/request").contentType("application/json")
                        .content("{\"email\":\"revoked@example.test\"}"))
                .andExpect(status().isAccepted());
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        var match = Pattern.compile("token=([a-f0-9]{64})").matcher(message.getValue().getText());
        assertTrue(match.find());
        String token = match.group(1);

        mvc.perform(post("/api/authentication/password-reset/confirm").contentType("application/json")
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"lettersonly\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/authentication/password-reset/confirm").contentType("application/json")
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/fuel-orders").header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
        Thread.sleep(1100); // token iat has one-second resolution; a new one must be strictly later
        mvc.perform(post("/api/authentication/sign-in").contentType("application/json")
                        .content("{\"username\":\"revoked@example.test\",\"password\":\"NewPass123!\"}"))
                .andExpect(status().isOk());
    }
}
