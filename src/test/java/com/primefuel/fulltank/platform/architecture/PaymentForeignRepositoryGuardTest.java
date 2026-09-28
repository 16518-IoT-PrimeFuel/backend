package com.primefuel.fulltank.platform.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S23/T23-B acceptance guard: {@code payment} must not import another module's repositories (or any other
 * module's infrastructure). Before T23-B, {@code PaymentCommandServiceImpl} imported
 * {@code ordering.domain.repositories.FuelOrderRepository} and mutated the order directly; the cross-domain
 * write now travels through the {@code payment.completed.v1} event and ordering's own compatibility adapter.
 */
class PaymentForeignRepositoryGuardTest {

    private static final Path PAYMENT_ROOT =
            Path.of("src", "main", "java", "com", "primefuel", "fulltank", "platform", "payment");

    private static final Pattern FOREIGN_REPOSITORY_OR_INFRASTRUCTURE = Pattern.compile(
            "^import\\s+com\\.primefuel\\.fulltank\\.platform\\."
                    + "(?!payment\\.|shared\\.)"
                    + "[a-z]+\\."
                    + "(domain\\.repositories|infrastructure)\\.");

    @Test
    void paymentImportsNoForeignRepository() throws IOException {
        assertThat(Files.isDirectory(PAYMENT_ROOT)).isTrue();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(PAYMENT_ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (FOREIGN_REPOSITORY_OR_INFRASTRUCTURE.matcher(line.strip()).find()) {
                        violations.add(file + " -> " + line.strip());
                    }
                }
            }
        }

        assertThat(violations)
                .as("payment must not import another module's repository/infrastructure; cross-domain effects "
                        + "travel through events (payment.completed.v1) and the owning module's adapter")
                .isEmpty();
    }
}
