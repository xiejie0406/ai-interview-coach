package com.ruoyi.fashion.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.ruoyi.common.exception.ServiceException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class FashionCatalogValueImportParserTest {
    private final FashionCatalogValueImportParser parser = new FashionCatalogValueImportParser();

    @Test
    void parsesBomQuotedAndMultilineCsvWithoutLosingLeadingZeros() {
        byte[] csv = ("\ufeff来源编码,SKU编码,销售单价,币种,含税口径,业务时间,备注\n"
                + "\"MANUAL\",\"000123\",199.00,CNY,included,2026-09-13T00:00:00Z,\"第一行\n第二行\"\n")
                .getBytes(StandardCharsets.UTF_8);
        var rows = parser.parse(CatalogImportType.PRICE, "price.csv", csv);
        assertEquals(1, rows.size());
        assertEquals("000123", rows.get(0).values().get("skuCode"));
        assertEquals("199.00", rows.get(0).values().get("salePrice"));
    }

    @Test
    void rejectsFormulaAndNumericCodeInXlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("价格");
            var header = sheet.createRow(0);
            String[] headers = {"来源编码", "SKU编码", "销售单价", "币种", "含税口径", "业务时间"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("MANUAL");
            row.createCell(1).setCellValue(123);
            row.createCell(2).setCellFormula("100+99");
            row.createCell(3).setCellValue("CNY");
            row.createCell(4).setCellValue("included");
            row.createCell(5).setCellValue("2026-09-13T00:00:00Z");
            workbook.write(output);
            var parsed = parser.parse(CatalogImportType.PRICE, "price.xlsx", output.toByteArray());
            assertTrue(parsed.get(0).errors().stream().anyMatch(error -> "code_must_be_text".equals(error.code())));
            assertTrue(parsed.get(0).errors().stream().anyMatch(error -> "formula_not_allowed".equals(error.code())));
        }
    }

    @Test
    void rejectsMultipleSheets() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("价格");
            workbook.createSheet("隐藏数据");
            workbook.write(output);
            assertThrows(ServiceException.class,
                    () -> parser.parse(CatalogImportType.PRICE, "price.xlsx", output.toByteArray()));
        }
    }
}
