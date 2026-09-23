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
 * T15-B acceptance guard: {@code fulfillment} must import <em>zero</em> foreign repositories (or other
 * modules' infrastructure). The ArchUnit boundary test only covers the modules listed in
 * {@code ModuleBoundaryRulesTest}; this one states the S15 definition-of-done directly ("cero imports a
 * repositorios ajenos desde delivery") and covers the modules ArchUnit does not (fleet/supply/replenishment).
 */
class FulfillmentForeignRepositoryGuardTest {

    private static final Path FULFILLMENT_ROOT =
            Path.of("src", "main", "java", "com", "primefuel", "fulltank", "platform", "fulfillment");

    private static final Pattern FOREIGN_REPOSITORY_OR_INFRASTRUCTURE = Pattern.compile(
            "^import\\s+com\\.primefuel\\.fulltank\\.platform\\."
                    + "(?!fulfillment\\.)"
                    + "(fleet|supply|replenishment|ordering|inventory|equipment|iam|telemetry|notification"
                    + "|payment|reporting|catalog)\\."
                    + "(domain\\.repositories|infrastructure)\\.");

    @Test
    void fulfillmentImportsNoForeignRepository() throws IOException {
        assertThat(Files.isDirectory(FULFILLMENT_ROOT))
                .as("the fulfillment source root must exist at %s", FULFILLMENT_ROOT.toAbsolutePath())
                .isTrue();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(FULFILLMENT_ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (FOREIGN_REPOSITORY_OR_INFRASTRUCTURE.matcher(line.strip()).find()) {
                        violations.add(file + " -> " + line.strip());
                    }
                }
            }
        }

        assertThat(violations)
                .as("fulfillment must not import another module's repository/infrastructure; external access goes "
                        + "through its public seams (fleet.api, supply.api, ordering query service) or the "
                        + "DeliveryIntegration port")
                .isEmpty();
    }
}
