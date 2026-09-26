package com.ruoyi.fashion.configuration.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class FashionApplicationConfigurationTest {
    @SuppressWarnings("unchecked")
    @Test
    void fashionMigrationDoesNotCaptureInterviewConfiguration() throws IOException {
        Map<String, Object> root = new Yaml().load(Files.readString(workspacePath(
                "ruoyi-backend", "ruoyi-admin", "src", "main", "resources", "application.yml")));
        Map<String, Object> interview = (Map<String, Object>) root.get("interview");
        Map<String, Object> voice = (Map<String, Object>) interview.get("voice-runtime");
        Map<String, Object> fashion = (Map<String, Object>) root.get("fashion");
        Map<String, Object> migration = (Map<String, Object>) fashion.get("migration");
        Map<String, Object> runtime = (Map<String, Object>) fashion.get("ai-runtime");
        Map<String, Object> image = (Map<String, Object>) fashion.get("image");
        Map<String, Object> serviceIdentity = (Map<String, Object>) fashion.get("service-identity");
        Map<String, Object> customerContact = (Map<String, Object>) fashion.get("customer-contact");

        assertTrue(voice.containsKey("local-storage-root"));
        assertTrue(interview.containsKey("providers"));
        assertEquals(java.util.Set.of("enabled", "expected-database", "baseline-approved"), migration.keySet());
        assertEquals(java.util.Set.of("enabled", "image", "migration", "ai-runtime", "service-identity", "customer-contact"),
                fashion.keySet());
        assertEquals("${FASHION_IMAGE_PROVIDER_ENABLED:false}", image.get("provider-enabled"));
        assertEquals("${FASHION_AI_RUNTIME_WORKER_ENABLED:false}", runtime.get("worker-enabled"));
        assertEquals("${FASHION_AI_RUNTIME_BASE_URL:}", runtime.get("base-url"));
        assertTrue(serviceIdentity.containsKey("active-key-base64"));
        assertTrue(customerContact.containsKey("active-key-base64"));
        assertEquals(null, serviceIdentity.get("active-key-base64"));
        assertEquals(null, customerContact.get("active-key-base64"));
    }

    private static Path workspacePath(String... parts) {
        Path current = Path.of("").toAbsolutePath();
        for (int up = 0; up < 5 && current != null; up++, current = current.getParent()) {
            Path candidate = current;
            for (String part : parts) {
                candidate = candidate.resolve(part);
            }
            if (Files.exists(candidate)) {
                return candidate.normalize();
            }
        }
        throw new IllegalStateException("无法定位 workspace 文件 " + String.join("/", parts));
    }
}
