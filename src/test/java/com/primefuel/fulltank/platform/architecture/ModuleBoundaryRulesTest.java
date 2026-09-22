package com.primefuel.fulltank.platform.architecture;

import com.primefuel.fulltank.platform.equipment.architecturefixture.SeededBoundaryViolation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class ModuleBoundaryRulesTest {

    private static final String ROOT = "com.primefuel.fulltank.platform";

    private static final List<String> BUSINESS_MODULES = List.of(
            "catalog",
            "equipment",
            "fulfillment",
            "iam",
            "inventory",
            "notification",
            "ordering",
            "payment",
            "reporting");

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages(ROOT);
    }

    private static ArchRule moduleMustNotReachIntoAnotherModulesInternals(String module) {
        String[] forbiddenPackages = BUSINESS_MODULES.stream()
                .filter(other -> !other.equals(module))
                .flatMap(other -> Stream.of(
                        ROOT + "." + other + ".domain..",
                        ROOT + "." + other + ".infrastructure..",
                        ROOT + "." + other + ".application.internal.."))
                .toArray(String[]::new);

        return noClasses()
                .that().resideInAPackage(ROOT + "." + module + "..")
                .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                .because("a module must interact with another module only through that module's public surface, "
                        + "never through its domain, infrastructure or internal application packages");
    }

    @Test
    void moduleBoundariesRespectTheFrozenBaseline() {
        JavaClasses classes = productionClasses();
        for (String module : BUSINESS_MODULES) {
            FreezingArchRule.freeze(moduleMustNotReachIntoAnotherModulesInternals(module)).check(classes);
        }
    }

    @Test
    void sharedKernelDoesNotDependOnBusinessModules() {
        String[] businessPackages = BUSINESS_MODULES.stream()
                .map(module -> ROOT + "." + module + "..")
                .toArray(String[]::new);

        noClasses()
                .that().resideInAPackage(ROOT + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(businessPackages)
                .because("shared is a technical kernel and must not know about any business module")
                .check(productionClasses());
    }

    @Test
    void boundaryRuleDetectsASeededViolation() {
        JavaClasses seededFixture = new ClassFileImporter().importClasses(SeededBoundaryViolation.class);
        ArchRule rule = moduleMustNotReachIntoAnotherModulesInternals("equipment");

        assertThat(rule.evaluate(seededFixture).hasViolation())
                .as("the boundary rule must flag a seeded cross-module internal dependency")
                .isTrue();
    }
}
