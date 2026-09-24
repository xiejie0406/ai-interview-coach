package com.ruoyi.fashion.infrastructure.files.pptx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.ruoyi.fashion.application.delivery.DeliveryTestFixtures;
import com.ruoyi.fashion.application.delivery.GeneratedDeliveryArtifact;
import com.ruoyi.fashion.application.material.port.FashionObjectStoragePort;
import com.ruoyi.fashion.application.material.port.StoredFashionObject;
import com.ruoyi.fashion.infrastructure.files.FashionDeliveryMedia;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;

class FashionQuotePptxRendererTest {
    @Test
    void createsEditableWidePresentationWithEmbeddedImagesAndContinuationPages() throws Exception {
        byte[] image = DeliveryTestFixtures.png();
        FashionQuotePptxRenderer renderer = new FashionQuotePptxRenderer(
                new FashionDeliveryMedia(storage(image)));
        List<GeneratedDeliveryArtifact> output = renderer.render(DeliveryTestFixtures.snapshot());

        assertThat(output).hasSize(1);
        assertThat(output.get(0).fileName()).endsWith(".pptx");
        assertThat(output.get(0).content()).isNotEmpty();
        Path validationDirectory = Path.of("target", "fashion-delivery-validation");
        Files.createDirectories(validationDirectory);
        Files.write(validationDirectory.resolve(output.get(0).fileName()), output.get(0).content());
        StringBuilder packageXml = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.get(0).content()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels")) {
                    packageXml.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        assertThat(packageXml.toString()).doesNotContain("成本", "内部备注", "secret-value", "traceparent");
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(output.get(0).content()))) {
            assertThat(ppt.getPageSize()).isEqualTo(FashionQuotePptxRenderer.PAGE);
            assertThat(ppt.getSlides().size()).isGreaterThanOrEqualTo(12);
            assertThat(ppt.getPictureData()).isNotEmpty();
            assertThat(ppt.getSlides().stream().flatMap(slide -> slide.getShapes().stream())
                    .filter(XSLFTable.class::isInstance).count()).isGreaterThan(3);
            String allText = ppt.getSlides().stream().flatMap(slide -> slide.getShapes().stream())
                    .filter(XSLFTextShape.class::isInstance).map(XSLFTextShape.class::cast)
                    .map(XSLFTextShape::getText).reduce("", (left, right) -> left + "\n" + right);
            String tableText = ppt.getSlides().stream().flatMap(slide -> slide.getShapes().stream())
                    .filter(XSLFTable.class::isInstance).map(XSLFTable.class::cast)
                    .flatMap(table -> table.getRows().stream()).flatMap(row -> row.getCells().stream())
                    .map(XSLFTextShape::getText).reduce("", (left, right) -> left + "\n" + right);
            assertThat(allText).contains("24050.00", "报价明细（续）");
            assertThat(tableText).contains("非常长的商品名称", "=FORMULA-SKU");

            XSLFTextShape editable = ppt.getSlides().get(0).getShapes().stream()
                    .filter(XSLFTextShape.class::isInstance).map(XSLFTextShape.class::cast).findFirst().orElseThrow();
            editable.setText(editable.getText() + " 已编辑");
            ByteArrayOutputStream saved = new ByteArrayOutputStream();
            ppt.write(saved);
            try (XMLSlideShow reopened = new XMLSlideShow(new ByteArrayInputStream(saved.toByteArray()))) {
                assertThat(reopened.getSlides().get(0).getShapes().stream()
                        .filter(XSLFTextShape.class::isInstance).map(XSLFTextShape.class::cast)
                        .map(XSLFTextShape::getText).anyMatch(text -> text.contains("已编辑"))).isTrue();
            }
        }
    }

    private static FashionObjectStoragePort storage(byte[] image) {
        String sha = FashionHashing.sha256(image);
        return new FashionObjectStoragePort() {
            @Override
            public StoredFashionObject putIfAbsent(String key, byte[] content, String contentType) {
                return new StoredFashionObject(key, FashionHashing.sha256(content), content.length, contentType);
            }

            @Override
            public byte[] read(String objectKey) {
                return image;
            }
        };
    }
}
