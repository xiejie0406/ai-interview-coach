package com.ruoyi.fashion.infrastructure.files.zip;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import tools.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.delivery.DeliveryQuoteSnapshot;
import com.ruoyi.fashion.application.delivery.GeneratedDeliveryArtifact;
import com.ruoyi.fashion.application.delivery.port.FashionDeliveryRenderer;
import com.ruoyi.fashion.infrastructure.files.DeliveryFileNames;
import com.ruoyi.fashion.infrastructure.files.FashionDeliveryMedia;
import com.ruoyi.fashion.infrastructure.storage.FashionHashing;
import org.springframework.stereotype.Component;

@Component
public final class FashionQuoteImageZipRenderer implements FashionDeliveryRenderer {
    private final FashionDeliveryMedia media;
    private final ObjectMapper mapper;

    public FashionQuoteImageZipRenderer(FashionDeliveryMedia media, ObjectMapper mapper) {
        this.media = media;
        this.mapper = mapper;
    }

    @Override
    public String fileType() { return "image_zip"; }

    @Override
    public List<GeneratedDeliveryArtifact> render(DeliveryQuoteSnapshot quote) {
        try {
            List<ImageEntry> images = collect(quote);
            if (images.isEmpty()) throw new ServiceException("报价没有可交付图片");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            List<Map<String, Object>> manifestFiles = new ArrayList<>();
            try (ZipOutputStream zip = new ZipOutputStream(output, java.nio.charset.StandardCharsets.UTF_8)) {
                for (ImageEntry image : images) {
                    ZipEntry entry = new ZipEntry(image.name());
                    entry.setTime(0L);
                    zip.putNextEntry(entry);
                    zip.write(image.content());
                    zip.closeEntry();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("file", image.name());
                    item.put("sha256", FashionHashing.sha256(image.content()));
                    item.put("bytes", image.content().length);
                    item.put("role", image.role());
                    item.put("combo_no", image.comboNo());
                    item.put("sku_codes", image.skuCodes());
                    item.put("source_type", image.sourceMode());
                    item.put("review_status", image.reviewStatus());
                    manifestFiles.add(item);
                }
                Map<String, Object> manifest = new LinkedHashMap<>();
                manifest.put("schema_version", "1.0");
                manifest.put("quote_no", quote.quoteNo());
                manifest.put("quote_version", quote.versionNo());
                manifest.put("quote_hash", quote.contentHash());
                manifest.put("generated_from", "confirmed-quote");
                manifest.put("files", manifestFiles);
                byte[] manifestBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
                ZipEntry manifestEntry = new ZipEntry("manifest.json");
                manifestEntry.setTime(0L);
                zip.putNextEntry(manifestEntry);
                zip.write(manifestBytes);
                zip.closeEntry();
            }
            return List.of(new GeneratedDeliveryArtifact(DeliveryFileNames.base(quote) + "_方案图片.zip",
                    "application/zip", output.toByteArray(), null, "image-package", allSkus(quote),
                    "mixed-confirmed", "pass"));
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("图片 ZIP 生成失败");
        }
    }

    private List<ImageEntry> collect(DeliveryQuoteSnapshot quote) {
        List<ImageEntry> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (DeliveryQuoteSnapshot.Combo combo : quote.combos()) {
            if (combo.adoptedImage() != null && keys.add(combo.adoptedImage().objectKey())) {
                byte[] bytes = media.readVerified(combo.adoptedImage().objectKey(), combo.adoptedImage().sha256());
                result.add(new ImageEntry(safeEntry(combo.comboNo() + "_采用图_01" + extension(bytes)), bytes,
                        "adopted", combo.comboNo(), combo.lines().stream().map(DeliveryQuoteSnapshot.Line::skuCode).toList(),
                        combo.adoptedImage().sourceMode(), combo.adoptedImage().reviewStatus()));
            }
            int index = 1;
            for (DeliveryQuoteSnapshot.Line line : combo.lines()) {
                if (line.imageKey() == null || !keys.add(line.imageKey())) continue;
                byte[] bytes = media.readVerified(line.imageKey(), line.imageHash());
                result.add(new ImageEntry(safeEntry(combo.comboNo() + "_原图_" + String.format("%02d", index++)
                        + "_" + line.skuCode() + extension(bytes)), bytes, "original", combo.comboNo(),
                        List.of(line.skuCode()), "product-original", "confirmed"));
            }
        }
        return List.copyOf(result);
    }

    private static String safeEntry(String value) {
        String safe = DeliveryFileNames.safe(value, 100);
        if (safe.contains("..") || safe.startsWith("/") || safe.contains("\\")) {
            throw new ServiceException("ZIP 文件名不符合安全规则");
        }
        return "images/" + safe;
    }

    private static String extension(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50) return ".png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) return ".jpg";
        throw new ServiceException("ZIP 仅支持 PNG/JPEG 交付图片");
    }

    private static List<String> allSkus(DeliveryQuoteSnapshot quote) {
        return quote.combos().stream().flatMap(c -> c.lines().stream())
                .map(DeliveryQuoteSnapshot.Line::skuCode).distinct().toList();
    }

    private record ImageEntry(String name, byte[] content, String role, String comboNo,
            List<String> skuCodes, String sourceMode, String reviewStatus) {
    }
}
