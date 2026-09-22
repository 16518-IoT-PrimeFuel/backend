package com.primefuel.fulltank.platform.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T01-A self-check: the ledger at docs/api-ledger/T01-A-rest-ledger.md must always describe
 * exactly the REST surface Spring registers at runtime under /api/v1/**. If a controller gains,
 * loses or renames a mapping without the ledger being updated, this test fails.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:contract_ledger_selfcheck;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class ApiLedgerSelfCheckTest {

    private static final int EXPECTED_OPERATION_COUNT = 77;
    private static final Pattern LEDGER_ROW = Pattern.compile(
            "^\\|\\s*\\d+\\s*\\|\\s*(GET|POST|PUT|PATCH|DELETE)\\s*\\|\\s*(`[^`]+`)\\s*\\|");

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void ledgerHas77RowsMatchingTheLiveApiV1RequestMappings() throws IOException {
        Set<String> runtimeOperations = collectRuntimeApiV1Operations();
        assertEquals(EXPECTED_OPERATION_COUNT, runtimeOperations.size(),
                "Runtime /api/v1/** mapping count drifted from the T01-A baseline of 77. "
                        + "Update docs/api-ledger/T01-A-rest-ledger.md if this is an intentional change.");

        Set<String> ledgerOperations = collectLedgerOperations();
        assertEquals(EXPECTED_OPERATION_COUNT, ledgerOperations.size(),
                "docs/api-ledger/T01-A-rest-ledger.md must list exactly 77 operations.");

        assertEquals(runtimeOperations, ledgerOperations,
                "Ledger rows and live /api/v1/** mappings diverged. Every method+path pair must match exactly.");
    }

    private Set<String> collectRuntimeApiV1Operations() {
        Set<String> operations = new TreeSet<>();
        Map<RequestMappingInfo, HandlerMethod> mappings = handlerMapping.getHandlerMethods();
        for (RequestMappingInfo info : mappings.keySet()) {
            Set<String> patterns = info.getPatternValues();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/v1/")) continue;
                for (RequestMethod method : methods) {
                    operations.add(method.name() + " " + pattern);
                }
            }
        }
        return operations;
    }

    private Set<String> collectLedgerOperations() throws IOException {
        Path ledgerPath = Path.of("docs", "api-ledger", "T01-A-rest-ledger.md");
        assertTrue(Files.exists(ledgerPath), "Missing ledger file at " + ledgerPath.toAbsolutePath());
        Set<String> operations = new TreeSet<>();
        for (String line : Files.readAllLines(ledgerPath, StandardCharsets.UTF_8)) {
            Matcher matcher = LEDGER_ROW.matcher(line.strip());
            if (!matcher.find()) continue;
            String httpMethod = matcher.group(1);
            String path = matcher.group(2).replace("`", "");
            operations.add(httpMethod + " " + path);
        }
        return operations;
    }
}
