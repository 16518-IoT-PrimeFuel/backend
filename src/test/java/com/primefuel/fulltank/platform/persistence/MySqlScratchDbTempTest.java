package com.primefuel.fulltank.platform.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

class MySqlScratchDbTempTest {

    @Test
    void dropScratchDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "root", "12345678");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS fulltank_baseline_check");
            System.out.println("SCRATCH_DROPPED");
        }
    }
}
