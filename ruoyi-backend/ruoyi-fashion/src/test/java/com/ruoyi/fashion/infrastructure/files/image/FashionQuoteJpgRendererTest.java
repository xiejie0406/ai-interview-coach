package com.ruoyi.fashion.infrastructure.files.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.ruoyi.fashion.application.delivery.DeliveryTestFixtures;
import com.ruoyi.fashion.application.material.port.FashionObjectStoragePort;
import com.ruoyi.fashion.application.material.port.StoredFashionObject;
import com.ruoyi.fashion.infrastructure.files.FashionDeliveryMedia;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import org.junit.jupiter.api.Test;

class FashionQuoteJpgRendererTest {
    @Test
    void convertsEverySelectedComboImageToRealJpeg() {
        byte[] image = DeliveryTestFixtures.png();
        FashionObjectStoragePort storage = new FashionObjectStoragePort() {
            @Override public StoredFashionObject putIfAbsent(String key, byte[] content, String type) {
                return new StoredFashionObject(key, FashionHashing.sha256(content), content.length, type);
            }
            @Override public byte[] read(String key) { return image; }
        };
        var files = new FashionQuoteJpgRenderer(new FashionDeliveryMedia(storage))
                .render(DeliveryTestFixtures.snapshot());
        assertThat(files).hasSize(4);
        files.forEach(file -> {
            assertThat(file.fileName()).endsWith(".jpg");
            assertThat(file.content()[0]).isEqualTo((byte) 0xFF);
            assertThat(file.content()[1]).isEqualTo((byte) 0xD8);
            assertThat(file.content()[file.content().length - 2]).isEqualTo((byte) 0xFF);
            assertThat(file.content()[file.content().length - 1]).isEqualTo((byte) 0xD9);
        });
    }
}
