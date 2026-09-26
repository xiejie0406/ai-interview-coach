package com.ruoyi.fashion.application.importing;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.infrastructure.storage.FashionUploadPolicy;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/** 价格与库存固定模板的有界流式解析器。 */
@FashionModuleEnabled
@Component
public final class FashionCatalogValueImportParser {
    public static final String TEMPLATE_VERSION = "1.0";
    /** 与 P0 单批价格/库存容量一致；超过上限在产生业务写入前拒绝。 */
    public static final int MAX_ROWS = 100_000;
    private static final Set<String> TEXT_ONLY_CODES = Set.of("sourceCode", "skuCode", "warehouseCode");

    public List<ParsedCatalogValueRow> parse(CatalogImportType type, String filename, byte[] content) {
        FashionUploadPolicy.validateImport(filename, content);
        return "csv".equals(FashionUploadPolicy.extension(filename))
                ? parseCsv(type, content)
                : parseXlsx(type, content);
    }

    private List<ParsedCatalogValueRow> parseCsv(CatalogImportType type, byte[] content) {
        String text = FashionUploadPolicy.decodeUtf8(content);
        if (text.indexOf('\0') >= 0) {
            throw new ServiceException("CSV 包含 NUL 字节");
        }
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false).setTrim(false).get();
        try (CSVParser parser = format.parse(new StringReader(text))) {
            requireHeaders(parser.getHeaderNames(), type.headers());
            List<ParsedCatalogValueRow> result = new ArrayList<>();
            for (CSVRecord record : parser) {
                requireCapacity(result, type);
                Map<String, String> values = new LinkedHashMap<>();
                type.headers().forEach((field, header) -> values.put(field, normalize(record.get(header))));
                if (values.values().stream().allMatch(value -> value == null || value.isBlank())) {
                    continue;
                }
                List<ImportRowError> errors = requiredErrors(values);
                values.forEach((field, value) -> {
                    if (looksLikeFormula(value)) {
                        errors.add(new ImportRowError(field, "csv_formula_prefix", "[formula-like]",
                                "CSV 单元格不能以公式控制字符开头"));
                    }
                });
                result.add(new ParsedCatalogValueRow((int) record.getRecordNumber() + 1,
                        Collections.unmodifiableMap(new LinkedHashMap<>(values)), List.copyOf(errors)));
            }
            requireRows(result, type);
            return List.copyOf(result);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ServiceException("CSV 结构无效：" + exception.getMessage());
        }
    }

    private List<ParsedCatalogValueRow> parseXlsx(CatalogImportType type, byte[] content) {
        List<StreamingXlsxReader.RowValue> rows = StreamingXlsxReader.read(content, MAX_ROWS + 2);
        if (rows.isEmpty()) {
            throw new ServiceException("XLSX 缺少表头");
        }
        Map<String, Integer> headers = headers(rows.get(0));
        requireHeaders(new ArrayList<>(headers.keySet()), type.headers());
        List<ParsedCatalogValueRow> result = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            requireCapacity(result, type);
            StreamingXlsxReader.RowValue row = rows.get(rowIndex);
            Map<String, String> values = new LinkedHashMap<>();
            List<ImportRowError> errors = new ArrayList<>();
            for (Map.Entry<String, String> entry : type.headers().entrySet()) {
                StreamingXlsxReader.CellValue cell = row.cells().get(headers.get(entry.getValue()));
                if (cell != null && cell.formula()) {
                    errors.add(new ImportRowError(entry.getKey(), "formula_not_allowed", "[formula]",
                            "不接受公式单元格，请粘贴为值"));
                    values.put(entry.getKey(), null);
                } else {
                    if (cell != null && cell.numeric() && TEXT_ONLY_CODES.contains(entry.getKey())) {
                        errors.add(new ImportRowError(entry.getKey(), "code_must_be_text", "[numeric]",
                                "编码字段必须设置为文本，避免前导零丢失"));
                    }
                    values.put(entry.getKey(), normalize(cell == null ? null : cell.value()));
                }
            }
            if (values.values().stream().allMatch(value -> value == null || value.isBlank()) && errors.isEmpty()) {
                continue;
            }
            errors.addAll(requiredErrors(values));
            result.add(new ParsedCatalogValueRow(row.rowNumber(),
                    Collections.unmodifiableMap(new LinkedHashMap<>(values)), List.copyOf(errors)));
        }
        requireRows(result, type);
        return List.copyOf(result);
    }

    private static Map<String, Integer> headers(StreamingXlsxReader.RowValue row) {
        Map<String, Integer> result = new LinkedHashMap<>();
        int maximumColumn = row.cells().keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int index = 0; index <= maximumColumn; index++) {
            StreamingXlsxReader.CellValue cell = row.cells().get(index);
            if (cell == null || cell.formula()) {
                throw new ServiceException("表头不能为空或使用公式");
            }
            String value = normalize(cell.value());
            if (value == null || result.putIfAbsent(value, index) != null) {
                throw new ServiceException("XLSX 存在空白或重复表头：" + value);
            }
        }
        return result;
    }

    private static void requireHeaders(List<String> actual, Map<String, String> expected) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        actual.forEach(header -> { if (!seen.add(header)) duplicates.add(header); });
        if (!duplicates.isEmpty()) {
            throw new ServiceException("导入文件存在重复表头：" + duplicates);
        }
        Set<String> missing = new HashSet<>(expected.values());
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            throw new ServiceException("导入文件缺少固定模板列：" + missing);
        }
    }

    private static List<ImportRowError> requiredErrors(Map<String, String> values) {
        List<ImportRowError> result = new ArrayList<>();
        values.forEach((field, value) -> {
            if (value == null || value.isBlank()) {
                result.add(new ImportRowError(field, "required", "[empty]", field + " 不能为空"));
            }
        });
        return result;
    }

    private static void requireCapacity(List<?> rows, CatalogImportType type) {
        if (rows.size() >= MAX_ROWS) {
            throw new ServiceException(type.code() + " 导入最多 " + MAX_ROWS + " 行");
        }
    }

    private static void requireRows(List<?> rows, CatalogImportType type) {
        if (rows.isEmpty()) {
            throw new ServiceException(type.code() + " 导入文件没有数据行");
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean looksLikeFormula(String value) {
        return value != null && !value.isEmpty()
                && (value.charAt(0) == '=' || value.charAt(0) == '+' || value.charAt(0) == '@'
                || value.charAt(0) == '\t' || value.charAt(0) == '\r');
    }
}
