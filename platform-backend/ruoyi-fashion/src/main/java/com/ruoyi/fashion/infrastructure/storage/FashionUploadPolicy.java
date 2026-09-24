package com.ruoyi.fashion.infrastructure.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import com.ruoyi.common.exception.ServiceException;

/** 上传内容的 magic、资源上限和路径边界；不能只信文件名或 Content-Type。 */
public final class FashionUploadPolicy {
    public static final int MAX_IMPORT_BYTES = 30 * 1024 * 1024;
    public static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;
    public static final int MAX_ARCHIVE_BYTES = 200 * 1024 * 1024;
    public static final long MAX_ARCHIVE_UNCOMPRESSED_BYTES = 500L * 1024 * 1024;
    public static final int MAX_ARCHIVE_ENTRIES = 1_000;
    public static final int MAX_ARCHIVE_EXPANSION_RATIO = 100;
    public static final int MIN_IMAGE_WIDTH = 200;
    public static final int MIN_IMAGE_HEIGHT = 200;
    private static final long MAX_PIXELS = 40_000_000L;

    private FashionUploadPolicy() {
    }

    public static String safeFilename(String value) {
        if (value == null || value.isBlank() || value.length() > 255
                || value.contains("/") || value.contains("\\") || value.contains("\0")
                || value.equals(".") || value.equals("..")) {
            throw new ServiceException("文件名无效");
        }
        return value.trim();
    }

    public static void validateImport(String filename, byte[] content) {
        safeFilename(filename);
        requireSize(content, MAX_IMPORT_BYTES, "导入文件");
        String extension = extension(filename);
        if ("csv".equals(extension)) {
            decodeUtf8(content);
            return;
        }
        if (!"xlsx".equals(extension) || !startsWith(content, 0x50, 0x4b, 0x03, 0x04)) {
            throw new ServiceException("商品导入只接受 UTF-8 CSV 或非加密 XLSX");
        }
        inspectOfficeZip(content);
    }

    public static ImageInspection validateImage(String filename, byte[] content) {
        safeFilename(filename);
        requireSize(content, MAX_IMAGE_BYTES, "图片");
        String extension = extension(filename);
        String contentType;
        int width;
        int height;
        try {
            if (startsWith(content, 0xff, 0xd8, 0xff) && SetSupport.isOneOf(extension, "jpg", "jpeg")) {
                java.awt.image.BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
                if (image == null) {
                    throw new ServiceException("JPEG 图片损坏");
                }
                width = image.getWidth();
                height = image.getHeight();
                contentType = "image/jpeg";
            } else if (startsWith(content, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
                    && "png".equals(extension)) {
                java.awt.image.BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
                if (image == null) {
                    throw new ServiceException("PNG 图片损坏");
                }
                width = image.getWidth();
                height = image.getHeight();
                contentType = "image/png";
            } else if ("webp".equals(extension) && startsWith(content, 0x52, 0x49, 0x46, 0x46)
                    && content.length >= 30 && new String(content, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
                int[] dimensions = webpDimensions(content);
                width = dimensions[0];
                height = dimensions[1];
                contentType = "image/webp";
            } else {
                throw new ServiceException("图片扩展名与 magic 不一致，仅支持 JPG、PNG、WebP");
            }
        } catch (IOException exception) {
            throw new ServiceException("图片损坏或无法读取");
        }
        if (width < MIN_IMAGE_WIDTH || height < MIN_IMAGE_HEIGHT || (long) width * height > MAX_PIXELS) {
            throw new ServiceException("图片像素不符合 200×200 至 4000 万像素限制");
        }
        return new ImageInspection(contentType, width, height, extension);
    }

    public static List<ArchiveEntry> extractImages(byte[] zip) {
        return extractRawEntries(zip).stream()
                .map(entry -> new ArchiveEntry(
                        entry.filename(), entry.content(), validateImage(entry.filename(), entry.content())))
                .toList();
    }

    public static List<RawArchiveEntry> extractRawEntries(byte[] zip) {
        requireSize(zip, MAX_ARCHIVE_BYTES, "图片 ZIP");
        if (!startsWith(zip, 0x50, 0x4b, 0x03, 0x04)) {
            throw new ServiceException("图片压缩包 magic 无效");
        }
        List<RawArchiveEntry> result = new ArrayList<>();
        long total = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")
                        || java.util.Arrays.asList(name.split("/")).contains("..")) {
                    throw new ServiceException("ZIP 包含路径越界条目");
                }
                String filename = name.substring(name.lastIndexOf('/') + 1);
                safeFilename(filename);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_ARCHIVE_UNCOMPRESSED_BYTES
                            || total > Math.max(zip.length, 1) * (long) MAX_ARCHIVE_EXPANSION_RATIO
                            || output.size() + read > MAX_IMAGE_BYTES) {
                        throw new ServiceException("ZIP 解压后大小超限");
                    }
                    output.write(buffer, 0, read);
                }
                byte[] content = output.toByteArray();
                result.add(new RawArchiveEntry(filename, content));
                if (result.size() > MAX_ARCHIVE_ENTRIES) {
                    throw new ServiceException("ZIP 图片数量超过 " + MAX_ARCHIVE_ENTRIES);
                }
            }
        } catch (IOException exception) {
            throw new ServiceException("ZIP 损坏或无法解压");
        }
        if (result.isEmpty()) {
            throw new ServiceException("ZIP 中没有可用图片");
        }
        return List.copyOf(result);
    }

    public static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String decodeUtf8(byte[] value) {
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value)).toString();
            return decoded.startsWith("\ufeff") ? decoded.substring(1) : decoded;
        } catch (CharacterCodingException exception) {
            throw new ServiceException("CSV 必须使用有效 UTF-8 编码");
        }
    }

    private static void inspectOfficeZip(byte[] content) {
        boolean workbook = false;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            int entries = 0;
            long total = 0;
            while ((entry = input.getNextEntry()) != null) {
                entries++;
                if (entries > 500) {
                    throw new ServiceException("XLSX 内部条目过多");
                }
                String name = entry.getName();
                if ("xl/workbook.xml".equals(name)) {
                    workbook = true;
                }
                if (name.endsWith("vbaProject.bin") || name.contains("externalLinks")) {
                    throw new ServiceException("不接受含宏或外部链接的工作簿");
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > 80L * 1024 * 1024) {
                        throw new ServiceException("XLSX 解压内容超限");
                    }
                }
            }
        } catch (IOException exception) {
            throw new ServiceException("XLSX 损坏或被加密");
        }
        if (!workbook) {
            throw new ServiceException("XLSX 缺少工作簿内容");
        }
    }

    private static int[] webpDimensions(byte[] content) {
        String chunk = new String(content, 12, 4, StandardCharsets.US_ASCII);
        if ("VP8X".equals(chunk) && content.length >= 30) {
            int width = 1 + littleEndian24(content, 24);
            int height = 1 + littleEndian24(content, 27);
            return new int[] {width, height};
        }
        if ("VP8L".equals(chunk) && content.length >= 25 && (content[20] & 0xff) == 0x2f) {
            int bits = ByteBuffer.wrap(content, 21, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            return new int[] {(bits & 0x3fff) + 1, ((bits >>> 14) & 0x3fff) + 1};
        }
        if ("VP8 ".equals(chunk) && content.length >= 30
                && (content[23] & 0xff) == 0x9d && (content[24] & 0xff) == 0x01
                && (content[25] & 0xff) == 0x2a) {
            int width = ByteBuffer.wrap(content, 26, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0x3fff;
            int height = ByteBuffer.wrap(content, 28, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0x3fff;
            return new int[] {width, height};
        }
        throw new ServiceException("不支持或损坏的 WebP 编码");
    }

    private static int littleEndian24(byte[] value, int offset) {
        return (value[offset] & 0xff) | ((value[offset + 1] & 0xff) << 8) | ((value[offset + 2] & 0xff) << 16);
    }

    private static void requireSize(byte[] content, int maximum, String label) {
        if (content == null || content.length == 0 || content.length > maximum) {
            throw new ServiceException(label + "为空或超过大小限制");
        }
    }

    private static boolean startsWith(byte[] value, int... bytes) {
        if (value == null || value.length < bytes.length) {
            return false;
        }
        for (int index = 0; index < bytes.length; index++) {
            if ((value[index] & 0xff) != bytes[index]) {
                return false;
            }
        }
        return true;
    }

    public record ImageInspection(String contentType, int width, int height, String extension) {
    }

    public record ArchiveEntry(String filename, byte[] content, ImageInspection inspection) {
        public ArchiveEntry {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    public record RawArchiveEntry(String filename, byte[] content) {
        public RawArchiveEntry {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    private static final class SetSupport {
        private SetSupport() {
        }

        static boolean isOneOf(String value, String... allowed) {
            for (String candidate : allowed) {
                if (candidate.equals(value)) {
                    return true;
                }
            }
            return false;
        }
    }
}
