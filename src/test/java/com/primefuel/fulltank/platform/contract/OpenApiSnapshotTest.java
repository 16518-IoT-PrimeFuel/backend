package com.primefuel.fulltank.platform.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T01-A OpenAPI snapshot: captures the live springdoc contract for /api/v1/**, normalizes it
 * (sorted keys, no timestamps/generated IDs are ever part of an OpenAPI document) and writes a
 * deterministic copy to docs/api-ledger/openapi-snapshot.json so future tickets can diff against
 * the AS-IS contract instead of guessing.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:contract_openapi_snapshot;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class OpenApiSnapshotTest {

    private static final Path SNAPSHOT_PATH = Path.of("docs", "api-ledger", "openapi-snapshot.json");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void apiDocsAreCapturedAsADeterministicSortedSnapshot() throws Exception {
        String raw = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(raw);
        JsonNode paths = root.get("paths");
        assertTrue(paths != null && paths.size() > 0, "OpenAPI document has no paths");

        long operationCount = 0;
        for (String pathName : paths.propertyNames()) {
            if (!pathName.startsWith("/api/v1/")) continue;
            operationCount += paths.get(pathName).size();
        }
        assertEquals(77, operationCount,
                "springdoc's /api-docs no longer describes 77 operations under /api/v1/**. "
                        + "Update docs/api-ledger/T01-A-rest-ledger.md if this is intentional.");

        String deterministicJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(sortDeep(root));
        Files.createDirectories(SNAPSHOT_PATH.getParent());
        Files.writeString(SNAPSHOT_PATH, deterministicJson + System.lineSeparator());
    }

    /** Recursively rewrites every JSON object with a TreeMap-backed ObjectNode so key order is deterministic. */
    private JsonNode sortDeep(JsonNode node) {
        if (node.isObject()) {
            Map<String, JsonNode> sorted = new TreeMap<>();
            node.properties().forEach(entry -> sorted.put(entry.getKey(), sortDeep(entry.getValue())));
            ObjectNode result = objectMapper.createObjectNode();
            sorted.forEach(result::set);
            return result;
        }
        if (node.isArray()) {
            var result = objectMapper.createArrayNode();
            node.forEach(child -> result.add(sortDeep(child)));
            return result;
        }
        return node;
    }
}
