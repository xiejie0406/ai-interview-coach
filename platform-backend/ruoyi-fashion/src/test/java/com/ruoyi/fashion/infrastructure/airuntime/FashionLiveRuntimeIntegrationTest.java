package com.ruoyi.fashion.infrastructure.airuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.fashion.application.agent.run.FashionRuntimeException;
import com.ruoyi.fashion.application.agent.run.RunWorkItem;
import com.ruoyi.fashion.configuration.FashionAiRuntimeProperties;
import com.ruoyi.fashion.configuration.telemetry.FashionTelemetry;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceAuthHeaders;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceIdentity;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceIdentityKeyRing;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceIdentityPolicy;
import com.ruoyi.fashion.infrastructure.airuntime.security.FashionServiceRequest;
import com.ruoyi.fashion.infrastructure.airuntime.security.HmacSha256FashionServiceIdentity;
import com.ruoyi.fashion.infrastructure.airuntime.security.InMemoryFashionServiceReplayStore;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * 由隔离脚本启动真实 Uvicorn 后执行；普通 Maven 回归没有端口和密钥时保持跳过。
 */
@EnabledIfSystemProperty(named = "fashion.live-runtime.url", matches = "http://127\\.0\\.0\\.1:[0-9]+")
class FashionLiveRuntimeIntegrationTest {

    private static final String KEY_ID = "live-runtime-test";
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final String CORRELATION_ID = "live-runtime-correlation-001";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void javaSignerAuthenticatesCapabilityAndPreservesTraceContext() throws Exception {
        String baseUrl = requiredProperty("fashion.live-runtime.url");
        FashionServiceIdentity identity = identity();
        String path = "/internal/v1/capabilities";
        FashionServiceAuthHeaders auth = identity.sign(new FashionServiceRequest("GET", path, new byte[0]));

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("X-Request-Id", UUID.randomUUID().toString())
                .header("X-Correlation-Id", CORRELATION_ID)
                .header("traceparent", TRACEPARENT)
                .GET();
        auth.toMap().forEach(request::header);

        HttpResponse<byte[]> response = liveHttpClient()
                .send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        JsonNode body = mapper.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertEquals("fashion-ai-runtime", body.path("service").asText());
        assertFalse(body.path("provider").path("enabled").asBoolean(true));
        assertEquals(4, body.path("operations").size());
        assertEquals(CORRELATION_ID,
                response.headers().firstValue("X-Correlation-Id").orElseThrow());
        String returnedTraceparent = response.headers().firstValue("traceparent").orElseThrow();
        assertTrue(returnedTraceparent.matches("00-" + TRACE_ID + "-[a-f0-9]{16}-01"));
    }

    @Test
    void javaRuntimeAdapterMapsAuthenticatedDisabledProviderResponse() {
        FashionAiRuntimeProperties properties = new FashionAiRuntimeProperties();
        properties.setBaseUrl(requiredProperty("fashion.live-runtime.url"));
        properties.setConnectTimeout(Duration.ofSeconds(5));
        HttpFashionRequirementRuntimeAdapter adapter = new HttpFashionRequirementRuntimeAdapter(
                properties,
                identity(),
                mapper,
                new FashionTelemetry(OpenTelemetry.noop()));

        FashionRuntimeException failure = assertThrows(
                FashionRuntimeException.class,
                () -> adapter.analyze(workItem()));

        assertEquals("PROVIDER_DISABLED", failure.code());
        assertFalse(failure.retryable());
        assertEquals("AI Provider 未启用，需求分析暂不可用", failure.getMessage());
    }

    @Test
    void javaSignerAuthenticatesNonEmptyPostBeforeContractValidation() throws Exception {
        String baseUrl = requiredProperty("fashion.live-runtime.url");
        String path = "/internal/v1/requirement-analysis";
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        FashionServiceAuthHeaders auth = identity().sign(new FashionServiceRequest("POST", path, body));

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Request-Id", UUID.randomUUID().toString())
                .header("X-Correlation-Id", CORRELATION_ID)
                .header("traceparent", TRACEPARENT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        auth.toMap().forEach(request::header);

        HttpResponse<byte[]> response = liveHttpClient()
                .send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        JsonNode responseBody = mapper.readTree(response.body());

        assertEquals(422, response.statusCode());
        assertEquals("REQUEST_VALIDATION_FAILED",
                responseBody.path("error").path("code").asText());
    }

    private FashionServiceIdentity identity() {
        byte[] key = Base64.getDecoder().decode(requiredProperty("fashion.live-runtime.key-base64"));
        return new HmacSha256FashionServiceIdentity(
                FashionServiceIdentityPolicy.javaControlPlaneDefaults(),
                FashionServiceIdentityKeyRing.of(KEY_ID, Map.of(KEY_ID, key)),
                new InMemoryFashionServiceReplayStore(),
                Clock.systemUTC(),
                () -> "live-" + UUID.randomUUID());
    }

    private static HttpClient liveHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private RunWorkItem workItem() {
        ObjectNode known = mapper.createObjectNode();
        known.put("audience", "企业员工");
        known.put("scene", "商务通勤");
        known.put("season", "春秋");
        known.put("style", "简约");
        known.putArray("preferred_colors").add("深蓝");
        known.putArray("exclusions").add("荧光色");

        ObjectNode comboTemplate = mapper.createObjectNode();
        ArrayNode groups = comboTemplate.putArray("groups");
        ObjectNode group = groups.addObject();
        group.put("count", 1);
        group.put("candidate_count", 3);
        group.putArray("slots").add("TOP");

        Instant now = Instant.now();
        return new RunWorkItem(
                1L,
                2L,
                "RUN-LIVE-0001",
                "analyze_requirement",
                3L,
                4L,
                5L,
                "disabled",
                "disabled",
                "config-live-001",
                6L,
                0L,
                7L,
                "CUST-LIVE-001",
                "需要一套适合春秋商务通勤的深蓝色服装方案",
                "source-live-001",
                known,
                20,
                new BigDecimal("300.00"),
                "per_set",
                false,
                comboTemplate,
                mapper.createObjectNode(),
                null,
                0L,
                mapper.createObjectNode(),
                "live-runtime-request-0001",
                now.plusSeconds(60),
                1,
                "lease-live-0001",
                "worker-live-0001",
                1L,
                now.plusSeconds(30),
                1);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少测试系统属性 " + name);
        }
        return value;
    }
}
