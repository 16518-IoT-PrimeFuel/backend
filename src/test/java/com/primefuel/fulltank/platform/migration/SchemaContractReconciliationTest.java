package com.primefuel.fulltank.platform.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaContractReconciliationTest {
    private static final Map<String, Set<String>> REQUIRED_COLUMNS = Map.ofEntries(
            Map.entry("organizations", Set.of("legacy_buyer_company_id", "legacy_provider_company_id")),
            Map.entry("memberships", Set.of("user_id", "organization_id", "status")),
            Map.entry("customer_accounts", Set.of("legacy_buyer_company_id")),
            Map.entry("customer_sites", Set.of("customer_account_id")),
            Map.entry("tanks", Set.of("legacy_equipment_id", "last_reading_at")),
            Map.entry("device_bindings", Set.of("credential_hash", "valid_from", "valid_until")),
            Map.entry("telemetry_readings", Set.of("event_id", "sequence_number", "captured_at", "received_at")),
            Map.entry("telemetry_inbox", Set.of("event_id", "attempts", "status")),
            Map.entry("telemetry_checkpoints", Set.of("device_id", "last_sequence_number")),
            Map.entry("refill_policies", Set.of("threshold_value", "hysteresis_value", "target_volume")),
            Map.entry("refill_episodes", Set.of("episode_key", "request_id", "status")),
            Map.entry("replenishment_request_lifecycle", Set.of("idempotency_key", "version", "consumed_order_id")),
            Map.entry("outbox_events", Set.of("event_key", "payload")),
            Map.entry("drivers", Set.of("license_expires_at")),
            Map.entry("vehicles", Set.of("enabled")),
            Map.entry("fleet_reservations", Set.of("idempotency_key", "window_start", "window_end")),
            Map.entry("notifications", Set.of("source_event_key")));

    @Test
    void allMigrationContractsExistInTheSyntheticSchema() throws Exception {
        var url = "jdbc:h2:mem:contract;MODE=MySQL;DB_CLOSE_DELAY=-1";
        var flyway = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration", "classpath:db/migration-test").load();
        flyway.migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            for (var entry : REQUIRED_COLUMNS.entrySet()) {
                for (var column : entry.getValue()) {
                    try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
                        statement.setString(1, entry.getKey().toUpperCase());
                        statement.setString(2, column.toUpperCase());
                        try (var result = statement.executeQuery()) {
                            result.next();
                            assertEquals(1, result.getInt(1), entry.getKey() + "." + column);
                        }
                    }
                }
            }
        }
        assertTrue(flyway.info().current().getVersion().getVersion().equals("19"));
    }
}
