package com.primefuel.fulltank.platform.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleBoundaryBaselineTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/primefuel/fulltank/platform");

    private static final Set<String> KNOWN_VIOLATIONS = Set.of(
            "iam/application/internal/commandservices/PasswordResetService.java:import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.PasswordResetTokenEntity;",
            "iam/application/internal/commandservices/PasswordResetService.java:import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.PasswordResetTokenRepository;",
            "ordering/application/internal/commandservices/FuelRequestService.java:import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.entities.FuelRequestPersistenceEntity;",
            "ordering/application/internal/commandservices/FuelRequestService.java:import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.repositories.FuelRequestPersistenceRepository;",
            "ordering/application/internal/commandservices/FuelRequestService.java:import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.CreateFuelRequestResource;"
    );

    @Test
    void applicationAndDomainDoNotAccumulateNewInfrastructureCrossings() throws IOException {
        var actual = new HashSet<String>();
        try (var files = Files.walk(SOURCE_ROOT)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var relative = SOURCE_ROOT.relativize(file).toString().replace('\u005c\u005c', '/');
                if (!relative.contains("/application/") && !relative.contains("/domain/")) {
                    continue;
                }
                var lines = Files.readAllLines(file);
                for (var line : lines) {
                    var importLine = line.trim();
                    if (importLine.startsWith("import ")
                            && (importLine.contains(".infrastructure.") || importLine.contains(".interfaces."))) {
                        actual.add(relative + ":" + importLine);
                    }
                }
            }
        }

        assertTrue(KNOWN_VIOLATIONS.containsAll(actual),
                () -> "New module-boundary violations: " + actual.stream()
                        .filter(violation -> !KNOWN_VIOLATIONS.contains(violation))
                        .collect(Collectors.joining(" | ")));
    }
}
