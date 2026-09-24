package com.ruoyi.fashion.infrastructure.files.zip;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import tools.jackson.databind.ObjectMapper;
import com.ruoyi.fashion.application.delivery.DeliveryTestFixtures;
import com.ruoyi.fashion.application.material.port.FashionObjectStoragePort;
import com.ruoyi.fashion.application.material.port.StoredFashionObject;
import com.ruoyi.fashion.infrastructure.files.FashionDeliveryMedia;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import org.junit.jupiter.api.Test;

class FashionQuoteImageZipRendererTest {
    @Test
    void createsSafeExtractableZipWithManifestAndDeduplicatedImages() throws Exception {
        byte[] image = DeliveryTestFixtures.png();
        FashionQuoteImageZipRenderer renderer = new FashionQuoteImageZipRenderer(
                new FashionDeliveryMedia(storage(image)), new ObjectMapper());
        byte[] content = renderer.render(DeliveryTestFixtures.snapshot()).get(0).content();
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                assertThat(entry.getName()).doesNotContain("..", "\\").doesNotStartWith("/");
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        assertThat(entries).containsKey("manifest.json");
        assertThat(entries.keySet().stream().filter(name -> name.startsWith("images/")).count()).isEqualTo(2);
        String manifest = new String(entries.get("manifest.json"), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(manifest).contains("FQ-1001", "quote_hash", "review_status", "adopted", "original");
        assertThat(manifest).doesNotContain("24050", "成本", "内部备注");
    }

    private static FashionObjectStoragePort storage(byte[] image) {
        return new FashionObjectStoragePort() {
            @Override public StoredFashionObject putIfAbsent(String key, byte[] content, String type) {
                return new StoredFashionObject(key, FashionHashing.sha256(content), content.length, type);
            }
            @Override public byte[] read(String key) { return image; }
        };
    }
}
