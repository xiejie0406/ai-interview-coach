package com.ruoyi.fashion.contract;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FashionContractBaselineTest {

    private static final Set<String> REQUIRED_ERROR_STATUSES = Set.of(
            "401", "403", "408", "409", "410", "413", "422", "429", "503", "504");

    @Test
    void consumesGatewayOpenApiAndSharedSchema() throws Exception {
        Path contractRoot = workspaceRoot().resolve("contracts/fashion");
        Map<String, Object> gateway = loadYaml(contractRoot.resolve("ai-control-gateway.openapi.yaml"));
        JsonNode common = new ObjectMapper().readTree(
                contractRoot.resolve("schemas/v1/internal-common.schema.json").toFile());

        assertThat(gateway.get("openapi")).isEqualTo("3.1.0");
        Map<String, Object> paths = map(gateway.get("paths"));
        assertThat(paths.keySet()).containsExactlyInAnyOrder(
                "/internal/v1/ai-run-steps:grant",
                "/internal/v1/ai-tools/{tool_name}:invoke",
                "/internal/v1/ai-run-events:append",
                "/internal/v1/ai-runs/{run_id}/control:read");
        paths.values().forEach(pathValue -> {
            Map<String, Object> post = map(map(pathValue).get("post"));
            assertThat(map(post.get("responses")).keySet()).containsAll(REQUIRED_ERROR_STATUSES);
        });

        JsonNode definitions = common.required("$defs");
        assertThat(definitions.required("contractVersion").required("const").asText()).isEqualTo("1.0");
        assertThat(definitions.required("attemptNo").required("minimum").asInt()).isEqualTo(1);
        assertThat(stringValues(definitions.required("stepType").required("enum")))
                .containsExactly("model", "tool", "handoff", "guardrail", "human_approval", "checkpoint");
        assertThat(stringValues(definitions.required("stepState").required("enum"))).contains("skipped");
        assertThat(stringValues(definitions.required("runState").required("enum")))
                .doesNotContain("timed_out", "TIMED_OUT");
    }

    @Test
    void everySharedJsonExampleIsSyntacticallyValid() throws Exception {
        Path examples = workspaceRoot().resolve("contracts/fashion/examples/v1");
        ObjectMapper mapper = new ObjectMapper();

        try (var files = Files.list(examples)) {
            List<Path> jsonFiles = files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            assertThat(jsonFiles).isNotEmpty();
            for (Path jsonFile : jsonFiles) {
                JsonNode root = mapper.readTree(jsonFile.toFile());
                assertThat(root.isObject() || root.isArray()).isTrue();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<String> stringValues(JsonNode array) {
        return array.valueStream().map(JsonNode::asText).toList();
    }

    private static Map<String, Object> loadYaml(Path path) throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (var reader = Files.newBufferedReader(path)) {
            return new Yaml(new SafeConstructor(options)).load(reader);
        }
    }

    private static Path workspaceRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("contracts/fashion/ai-runtime.openapi.yaml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("找不到 contracts/fashion 契约目录");
    }
}
