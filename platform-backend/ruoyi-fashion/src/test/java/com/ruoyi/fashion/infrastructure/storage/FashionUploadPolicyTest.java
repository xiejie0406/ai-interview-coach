package com.ruoyi.fashion.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

class FashionUploadPolicyTest {
    @Test
    void validatesPngByMagicAndPixels() throws Exception {
        BufferedImage image = new BufferedImage(300, 240, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);

        var inspection = FashionUploadPolicy.validateImage("sample.png", output.toByteArray());

        assertEquals("image/png", inspection.contentType());
        assertEquals(300, inspection.width());
        assertEquals(240, inspection.height());
    }

    @Test
    void readsLossyWebpDimensions() {
        byte[] webp = new byte[30];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);
        System.arraycopy("VP8 ".getBytes(StandardCharsets.US_ASCII), 0, webp, 12, 4);
        webp[23] = (byte) 0x9d;
        webp[24] = 0x01;
        webp[25] = 0x2a;
        webp[26] = 0x40;
        webp[27] = 0x01;
        webp[28] = (byte) 0xf0;

        var inspection = FashionUploadPolicy.validateImage("sample.webp", webp);

        assertEquals("image/webp", inspection.contentType());
        assertEquals(320, inspection.width());
        assertEquals(240, inspection.height());
    }

    @Test
    void rejectsExtensionMagicMismatchAndZipSlip() throws Exception {
        assertThrows(ServiceException.class,
                () -> FashionUploadPolicy.validateImage("fake.jpg", "not-an-image".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ServiceException.class,
                () -> FashionUploadPolicy.validateImage(
                        "too-large.png", new byte[FashionUploadPolicy.MAX_IMAGE_BYTES + 1]));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("../escape.png"));
            zip.write("bad".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThrows(ServiceException.class, () -> FashionUploadPolicy.extractRawEntries(output.toByteArray()));
    }

    @Test
    void rejectsArchiveExpansionRatioAboveOneHundred() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("compressed.bin"));
            zip.write(new byte[1024 * 1024]);
            zip.closeEntry();
        }

        assertThrows(ServiceException.class, () -> FashionUploadPolicy.extractRawEntries(output.toByteArray()));
    }
}
