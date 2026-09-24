package com.ruoyi.aden.application.idempotency;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdenRequestFingerprintTest {
    private final AdenRequestFingerprint fingerprint =
            new AdenRequestFingerprint(new ObjectMapper());

    @Test
    void objectFieldOrderDoesNotChangeTheHashAtAnyDepth() {
        String left = "{\"workspaceId\":\"w\",\"input\":{\"z\":1,\"a\":2},\"command\":\"START\"}";
        String right = "{\"command\":\"START\",\"input\":{\"a\":2,\"z\":1},\"workspaceId\":\"w\"}";

        assertEquals(fingerprint.hashJson(left), fingerprint.hashJson(right));
        assertEquals("{\"input\":{\"a\":2,\"z\":1},\"workspaceId\":\"w\"}",
                fingerprint.canonicalJson(new ObjectMapper().createObjectNode()
                        .put("workspaceId", "w")
                        .set("input", new ObjectMapper().createObjectNode().put("z", 1).put("a", 2))));
    }

    @Test
    void arrayOrderAndSemanticValuesRemainPartOfTheFingerprint() {
        assertNotEquals(fingerprint.hashJson("{\"items\":[1,2]}"),
                fingerprint.hashJson("{\"items\":[2,1]}"));
        assertNotEquals(fingerprint.hashJson("{\"version\":1}"),
                fingerprint.hashJson("{\"version\":2}"));
    }

    @Test
    void mapInsertionOrderDoesNotChangeHashValue() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("taskId", "t");
        first.put("expectedVersion", "7");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("expectedVersion", "7");
        second.put("taskId", "t");

        assertEquals(fingerprint.hashValue(first), fingerprint.hashValue(second));
        assertEquals(64, fingerprint.hashValue(List.of(first)).length());
    }

    @Test
    void taskPackageHashMatchesPythonCanonicalJsonVector() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("fixtureId", "fixture:success");
        input.put("instruction", "deterministic");
        input.put("expectedOutcome", "SUCCEED");
        Map<String, Object> taskPackage = new LinkedHashMap<>();
        taskPackage.put("schemaVersion", 1);
        taskPackage.put("taskId", "55555555-5555-4555-8555-555555555555");
        taskPackage.put("stepId", "66666666-6666-4666-8666-666666666666");
        taskPackage.put("taskType", "SYNTHETIC_CORE");
        taskPackage.put("capabilityCode", "CORE");
        taskPackage.put("attemptNo", 1);
        taskPackage.put("input", input);
        taskPackage.put("externalActionsEnabled", false);
        taskPackage.put("deadlineAt", "2026-09-13T00:00:00.123456Z");

        assertEquals("77dcc9fe78bbe09e1c6ba88e3ce0061e697f4c58dfc799fb5451212e6bca1e48",
                fingerprint.hashValue(taskPackage));
    }

    @Test
    void malformedJsonAndUnsafeIdempotencyKeysFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> fingerprint.hashJson("{"));
        assertThrows(IllegalArgumentException.class, () -> new AdenIdempotencyKey("contains space"));
        assertThrows(IllegalArgumentException.class, () -> new AdenIdempotencyKey("x".repeat(129)));
        assertEquals("request:abc-1", new AdenIdempotencyKey("request:abc-1").value());
        assertTrue(fingerprint.hashJson("null").matches("[a-f0-9]{64}"));
    }
}
