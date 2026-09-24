package com.primefuel.fulltank.platform.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticBaselineMigrationTest {

    @Test
    void migratesSchemaAndLoadsTestOnlyReferenceData() throws Exception {
        var url = "jdbc:h2:mem:synthetic;MODE=MySQL;DB_CLOSE_DELAY=-1";
        var flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration", "classpath:db/migration-test")
                .load();

        flyway.migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(1, count(connection, "fuel_products"));
            assertEquals(2, count(connection, "roles"));
            assertEquals(32, tableCount(connection));
        }
        assertTrue(flyway.info().current().getVersion().getVersion().equals("11"));
    }

    private static int count(java.sql.Connection connection, String table) throws Exception {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static int tableCount(java.sql.Connection connection) throws Exception {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE' AND TABLE_NAME <> 'flyway_schema_history'")) {
            result.next();
            return result.getInt(1);
        }
    }
}
