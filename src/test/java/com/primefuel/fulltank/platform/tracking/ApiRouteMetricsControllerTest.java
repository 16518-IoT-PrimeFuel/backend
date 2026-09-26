package com.primefuel.fulltank.platform.tracking;

import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:api_route_metrics;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class ApiRouteMetricsControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearMetrics() {
        jdbc.update("DELETE FROM api_route_callers");
        jdbc.update("DELETE FROM api_route_metrics");
    }

    @Test
    void countsPathPatternsAndDistinctTenantCallers() throws Exception {
        mockMvc.perform(get("/api/v1/users/7101").with(auth(7101L, 55L, "ROLE_BUYER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/7102").with(auth(7102L, 55L, "ROLE_BUYER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/7103").with(auth(7103L, 56L, "ROLE_BUYER")))
                .andExpect(status().isNotFound());

        var metric = jdbc.queryForMap("SELECT route_key, request_count FROM api_route_metrics "
                + "WHERE route_key = 'GET /api/v1/users/{userId}'");
        assertThat(metric.get("REQUEST_COUNT")).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM api_route_callers "
                + "WHERE route_key = 'GET /api/v1/users/{userId}'", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT MAX(version) FROM api_route_metrics "
                + "WHERE route_key = 'GET /api/v1/users/{userId}'", String.class)).isEqualTo("v1");
    }

    @Test
    void metricsReportRequiresAnAdmin() throws Exception {
        mockMvc.perform(get("/api/v2/admin/api-metrics").with(auth(7201L, 55L, "ROLE_BUYER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/admin/api-metrics").with(auth(7202L, null, "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor auth(Long id, Long companyId, String role) {
        var principal = new UserDetailsImpl(id, "metrics-user-" + id, "encoded", companyId, null,
                List.of(new SimpleGrantedAuthority(role)));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(token);
    }
}
