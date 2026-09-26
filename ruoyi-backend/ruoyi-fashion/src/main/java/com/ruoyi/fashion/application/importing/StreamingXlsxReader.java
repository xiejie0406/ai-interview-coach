package com.ruoyi.fashion.application.importing;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ruoyi.common.exception.ServiceException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/** 事件流读取单工作表 XLSX，避免为导入预览构造完整工作簿对象树。 */
final class StreamingXlsxReader {
    private StreamingXlsxReader() {
    }

    static List<RowValue> read(byte[] content, int maximumRows) {
        try (OPCPackage pack = OPCPackage.open(new ByteArrayInputStream(content))) {
            XSSFReader reader = new XSSFReader(pack, true);
            java.util.Iterator<InputStream> sheets = reader.getSheetsData();
            if (!sheets.hasNext()) {
                throw new ServiceException("XLSX 缺少工作表");
            }
            InputStream first = sheets.next();
            if (sheets.hasNext()) {
                first.close();
                throw new ServiceException("商品模板必须且只能包含一个工作表");
            }
            List<RowValue> result = new ArrayList<>();
            try (InputStream sheet = first) {
                XMLReader xmlReader = XMLHelper.newXMLReader();
                xmlReader.setContentHandler(new SheetHandler(
                        reader.getSharedStringsTable(), reader.getStylesTable(), result, maximumRows));
                xmlReader.parse(new InputSource(sheet));
            }
            return List.copyOf(result);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("XLSX 损坏、加密或结构无效");
        }
    }

    record RowValue(int rowNumber, Map<Integer, CellValue> cells) {
    }

    record CellValue(String value, boolean formula, boolean numeric) {
    }

    private static final class SheetHandler extends DefaultHandler {
        private final SharedStrings sharedStrings;
        private final StylesTable styles;
        private final List<RowValue> result;
        private final int maximumRows;
        private final DataFormatter formatter = new DataFormatter(Locale.ROOT, false);
        private Map<Integer, CellValue> rowCells;
        private int rowNumber;
        private int nextRowNumber = 1;
        private CellBuilder cell;
        private StringBuilder text;
        private boolean readingValue;
        private boolean readingInlineText;

        private SheetHandler(
                SharedStrings sharedStrings,
                StylesTable styles,
                List<RowValue> result,
                int maximumRows) {
            this.sharedStrings = sharedStrings;
            this.styles = styles;
            this.result = result;
            this.maximumRows = maximumRows;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String name = localName == null || localName.isEmpty() ? qName : localName;
            if ("row".equals(name)) {
                rowCells = new LinkedHashMap<>();
                String row = attributes.getValue("r");
                rowNumber = row == null ? nextRowNumber : Integer.parseInt(row);
                nextRowNumber = rowNumber + 1;
            } else if ("c".equals(name)) {
                String reference = attributes.getValue("r");
                int column = reference == null ? (rowCells == null ? 0 : rowCells.size())
                        : new CellReference(reference).getCol();
                cell = new CellBuilder(column, attributes.getValue("t"), parseInt(attributes.getValue("s")));
            } else if (cell != null && "f".equals(name)) {
                cell.formula = true;
            } else if (cell != null && "v".equals(name)) {
                readingValue = true;
                text = new StringBuilder();
            } else if (cell != null && "t".equals(name) && "inlineStr".equals(cell.type)) {
                readingInlineText = true;
            }
        }

        @Override
        public void characters(char[] chars, int start, int length) {
            if (readingValue) {
                text.append(chars, start, length);
            } else if (readingInlineText && cell != null) {
                cell.inlineText.append(chars, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String name = localName == null || localName.isEmpty() ? qName : localName;
            if ("v".equals(name) && cell != null) {
                cell.rawValue = text == null ? null : text.toString();
                readingValue = false;
                text = null;
            } else if ("t".equals(name)) {
                readingInlineText = false;
            } else if ("c".equals(name) && cell != null) {
                rowCells.put(cell.column, cellValue(cell));
                cell = null;
            } else if ("row".equals(name)) {
                result.add(new RowValue(rowNumber, Map.copyOf(rowCells)));
                if (result.size() > maximumRows) {
                    throw new ServiceException("商品导入最多 " + (maximumRows - 1) + " 行");
                }
                rowCells = null;
            }
        }

        private CellValue cellValue(CellBuilder builder) {
            String raw = "inlineStr".equals(builder.type) ? builder.inlineText.toString() : builder.rawValue;
            boolean numeric = builder.type == null || builder.type.isEmpty() || "n".equals(builder.type);
            if (raw == null) {
                return new CellValue(null, builder.formula, numeric);
            }
            try {
                if ("s".equals(builder.type)) {
                    return new CellValue(sharedStrings.getItemAt(Integer.parseInt(raw)).getString(),
                            builder.formula, false);
                }
                if ("b".equals(builder.type)) {
                    return new CellValue("1".equals(raw) ? "TRUE" : "FALSE", builder.formula, false);
                }
                if (numeric) {
                    double value = Double.parseDouble(raw);
                    if (builder.styleIndex != null) {
                        var style = styles.getStyleAt(builder.styleIndex);
                        return new CellValue(formatter.formatRawCellContents(
                                value, style.getDataFormat(), style.getDataFormatString()), builder.formula, true);
                    }
                }
                return new CellValue(raw, builder.formula, numeric);
            } catch (RuntimeException exception) {
                return new CellValue(raw, builder.formula, numeric);
            }
        }

        private static Integer parseInt(String value) {
            return value == null || value.isBlank() ? null : Integer.valueOf(value);
        }
    }

    private static final class CellBuilder {
        private final int column;
        private final String type;
        private final Integer styleIndex;
        private final StringBuilder inlineText = new StringBuilder();
        private String rawValue;
        private boolean formula;

        private CellBuilder(int column, String type, Integer styleIndex) {
            this.column = column;
            this.type = type;
            this.styleIndex = styleIndex;
        }
    }
}
