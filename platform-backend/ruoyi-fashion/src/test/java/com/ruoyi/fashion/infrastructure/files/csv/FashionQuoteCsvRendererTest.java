package com.ruoyi.fashion.infrastructure.files.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.ruoyi.fashion.application.delivery.DeliveryTestFixtures;
import org.junit.jupiter.api.Test;

class FashionQuoteCsvRendererTest {
    @Test
    void emitsUtf8BomExactAmountsAndNeutralizesSpreadsheetFormulaCells() {
        byte[] content = new FashionQuoteCsvRenderer().render(DeliveryTestFixtures.snapshot()).get(0).content();
        assertThat(content).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String csv = new String(content, StandardCharsets.UTF_8);
        assertThat(csv).contains("'=FORMULA-SKU", "24050.00", "最终应付");
        assertThat(FashionQuoteCsvRenderer.safe("  +cmd|' /C calc'!A0")).startsWith("'");
        assertThat(FashionQuoteCsvRenderer.safe("普通文本")).isEqualTo("普通文本");
    }
}
