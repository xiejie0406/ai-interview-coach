package com.ruoyi.fashion.application.delivery;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import tools.jackson.databind.ObjectMapper;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;

public final class DeliveryTestFixtures {
    private DeliveryTestFixtures() {
    }

    private static final byte[] IMAGE = buildPng();
    public static final String IMAGE_SHA = FashionHashing.sha256(IMAGE);

    public static DeliveryQuoteSnapshot snapshot() {
        ObjectMapper mapper = new ObjectMapper();
        List<DeliveryQuoteSnapshot.Combo> combos = new ArrayList<>();
        for (int comboIndex = 1; comboIndex <= 4; comboIndex++) {
            List<DeliveryQuoteSnapshot.Line> lines = new ArrayList<>();
            for (int line = 1; line <= (comboIndex == 4 ? 12 : comboIndex); line++) {
                lines.add(new DeliveryQuoteSnapshot.Line(line, "slot-" + line, 1000 + line,
                        line == 1 ? "=FORMULA-SKU" : "SKU-" + comboIndex + "-" + line,
                        "STYLE-" + line,
                        "非常长的商品名称用于验证自动换行和自动续页能力第" + line + "款",
                        "category-" + line, "深蓝色", "M", "件", line == 1 ? 60 : 40,
                        new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("6000.00"),
                        200, Instant.parse("2026-09-13T00:00:00Z"), "images/original.png", IMAGE_SHA, null));
            }
            combos.add(new DeliveryQuoteSnapshot.Combo(2000 + comboIndex, "C" + comboIndex,
                    comboIndex + "品类客户组合", comboIndex, 100, new BigDecimal("25000.00"),
                    new BigDecimal("1250.00"), new BigDecimal("300.00"), BigDecimal.ZERO,
                    new BigDecimal("24050.00"), new DeliveryQuoteSnapshot.ImageRef(
                            "images/adopted.png", IMAGE_SHA, "upload", "pass"), List.copyOf(lines)));
        }
        return new DeliveryQuoteSnapshot(1001, "FQ-1001", 3, "秋季企业团购长标题方案", "示例客户",
                100, "alternatives", "MAIN", "CNY", "included", BigDecimal.ZERO, "percent",
                new BigDecimal("5.00"), BigDecimal.ZERO, new BigDecimal("300.00"),
                new BigDecimal("25000.00"), new BigDecimal("1250.00"), BigDecimal.ZERO,
                new BigDecimal("24050.00"), 7, Instant.parse("2026-09-20T00:00:00Z"),
                "报价公开说明", "b".repeat(64), Instant.parse("2026-09-13T01:00:00Z"),
                mapper.createObjectNode().put("scene", "企业年会").put("audience", "员工")
                        .put("season", "秋季").put("style", "商务休闲"),
                mapper.createObjectNode().put("layout_version", "1.0"), List.copyOf(combos));
    }

    public static byte[] png() {
        return IMAGE.clone();
    }

    private static byte[] buildPng() {
        try {
            BufferedImage image = new BufferedImage(320, 480, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(40, 90, 160));
            graphics.fillRect(0, 0, 320, 480);
            graphics.setColor(Color.WHITE);
            graphics.drawString("FASHION", 110, 240);
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
