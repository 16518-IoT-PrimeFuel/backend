package com.primefuel.fulltank.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs every Flyway migration on an EMPTY disposable MySQL database and boots with
 * {@code ddl-auto=validate}, so a migration/entity mismatch (table plural naming, missing column, type)
 * fails here instead of at application start. Tests normally use H2 + Hibernate DDL and never see this.
 *
 * <p>Gated: set {@code MYSQL_SCHEMA_IT=1} (+ optional MYSQL_HOST/PORT/USER/PASSWORD). Only the schema
 * {@code fulltank_schema_validate} is dropped/recreated; {@code fulltank_platform} is never touched.
 */
@EnabledIfEnvironmentVariable(named = "MYSQL_SCHEMA_IT", matches = "1")
@SpringBootTest(properties = {"spring.profiles.active=test"})
class FlywaySchemaValidationMySqlTest {

    private static final String DATABASE = "fulltank_schema_validate";

    @Autowired DataSource dataSource;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) throws Exception {
        String host = env("MYSQL_HOST", "127.0.0.1");
        String port = env("MYSQL_PORT", "3306");
        String user = env("MYSQL_USER", "root");
        String password = env("MYSQL_PASSWORD", "");
        String base = "jdbc:mysql://" + host + ":" + port + "/";
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (var c = DriverManager.getConnection(base + options, user, password);
                var s = c.createStatement()) {
            s.execute("drop database if exists " + DATABASE);
            s.execute("create database " + DATABASE);
        }
        registry.add("spring.datasource.url", () -> base + DATABASE + options);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("authorization.jwt.secret", () -> "0123456789abcdef0123456789abcdef");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Test
    void allMigrationsApplyAndMatchTheEntities() throws Exception {
        try (var c = dataSource.getConnection();
                var rs = c.createStatement().executeQuery("select max(installed_rank) from flyway_schema_history where success = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isPositive();
        }
    }
}
