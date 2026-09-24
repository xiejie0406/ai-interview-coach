package com.ruoyi.fashion.infrastructure.files.pptx;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.delivery.DeliveryQuoteSnapshot;
import com.ruoyi.fashion.application.delivery.GeneratedDeliveryArtifact;
import com.ruoyi.fashion.application.delivery.port.FashionDeliveryRenderer;
import com.ruoyi.fashion.infrastructure.files.DeliveryFileNames;
import com.ruoyi.fashion.infrastructure.files.FashionDeliveryMedia;
import org.apache.poi.sl.usermodel.PictureData.PictureType;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

/** 16:9 原生 XSLF 报价演示文稿；文字、表格均可编辑，图片嵌入包内。 */
@Component
public final class FashionQuotePptxRenderer implements FashionDeliveryRenderer {
    static final Dimension PAGE = new Dimension(960, 540);
    private static final Color NAVY = new Color(23, 42, 73);
    private static final Color BLUE = new Color(38, 104, 181);
    private static final Color PALE = new Color(237, 244, 252);
    private static final Color INK = new Color(25, 35, 48);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    private final FashionDeliveryMedia media;

    public FashionQuotePptxRenderer(FashionDeliveryMedia media) {
        this.media = media;
    }

    @Override
    public String fileType() { return "pptx"; }

    @Override
    public List<GeneratedDeliveryArtifact> render(DeliveryQuoteSnapshot quote) {
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ppt.setPageSize(PAGE);
            cover(ppt, quote);
            requirements(ppt, quote);
            comparison(ppt, quote);
            for (DeliveryQuoteSnapshot.Combo combo : quote.combos()) comboPages(ppt, quote, combo);
            sourceImagePages(ppt, quote);
            detailPages(ppt, quote);
            terms(ppt, quote);
            ppt.write(output);
            String name = DeliveryFileNames.base(quote) + "_客户报价.pptx";
            return List.of(new GeneratedDeliveryArtifact(name,
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    output.toByteArray(), ppt.getSlides().size(), "customer-presentation", skuCodes(quote),
                    "frozen-quote", "confirmed"));
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("PPTX 生成失败");
        }
    }

    private void cover(XMLSlideShow ppt, DeliveryQuoteSnapshot quote) {
        XSLFSlide slide = baseSlide(ppt, null, 1);
        box(slide, 64, 95, 832, 75, quote.title(), 32, true, NAVY, TextAlign.CENTER);
        box(slide, 90, 190, 780, 46, "服装选品与报价方案", 22, false, BLUE, TextAlign.CENTER);
        box(slide, 120, 275, 720, 110,
                "客户：" + quote.customerName() + "\n方案：" + quote.quoteNo() + "  ·  版本 V" + quote.versionNo()
                        + "\n确认日期：" + (quote.confirmedAt() == null ? "—" : DATE.format(quote.confirmedAt())),
                18, false, INK, TextAlign.CENTER);
        box(slide, 120, 445, 720, 34, "本文件由单一已确认报价版本生成；系统外修改不会回写报价。",
                11, false, Color.DARK_GRAY, TextAlign.CENTER);
    }

    private void requirements(XMLSlideShow ppt, DeliveryQuoteSnapshot quote) {
        XSLFSlide slide = baseSlide(ppt, "需求与报价口径", 2);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"客户", quote.customerName()});
        rows.add(new String[] {"采购量", quote.requestedQty() + " 套"});
        rows.add(new String[] {"报价模式", "combined".equals(quote.quoteMode()) ? "合并采购" : "备选报价"});
        rows.add(new String[] {"场景", requirement(quote, "scene")});
        rows.add(new String[] {"人群", requirement(quote, "audience")});
        rows.add(new String[] {"季节 / 风格", requirement(quote, "season") + " / " + requirement(quote, "style")});
        rows.add(new String[] {"币种 / 税口径", quote.currency() + " / " + tax(quote)});
        rows.add(new String[] {"有效期", quote.validUntil() == null ? "—" : DATE.format(quote.validUntil())});
        nativeTable(slide, 95, 100, 770, 310, new String[] {"项目", "已确认内容"}, rows,
                new double[] {190, 580}, 17);
        box(slide, 95, 435, 770, 55, "品类档位：" + quote.combos().stream()
                .map(c -> c.categoryCount() + " 品类 · " + c.name()).reduce((a, b) -> a + "；" + b).orElse("—"),
                14, false, INK, TextAlign.LEFT);
    }

    private void comparison(XMLSlideShow ppt, DeliveryQuoteSnapshot quote) {
        XSLFSlide slide = baseSlide(ppt, "已选档位比较", 3);
        List<String[]> rows = new ArrayList<>();
        for (DeliveryQuoteSnapshot.Combo combo : quote.combos()) {
            rows.add(new String[] {combo.categoryCount() + " 品类", combo.name(), combo.setQty() + " 套",
                    money(combo.totalAmount()) + " " + quote.currency(), perSet(combo)});
        }
        nativeTable(slide, 45, 100, 870, Math.min(290, 58 + rows.size() * 48),
                new String[] {"档位", "组合", "数量", "采购金额", "每套"}, rows,
                new double[] {100, 270, 100, 200, 200}, 15);
        box(slide, 570, 385, 300, 30, "商品小计  " + money(quote.subtotal()), 16, false, INK, TextAlign.RIGHT);
        box(slide, 570, 420, 300, 30, "优惠  -" + money(quote.discountAmount()), 16, false, INK, TextAlign.RIGHT);
        box(slide, 570, 455, 300, 42, "最终应付  " + money(quote.totalAmount()) + " " + quote.currency(),
                22, true, BLUE, TextAlign.RIGHT);
    }

    private void comboPages(XMLSlideShow ppt, DeliveryQuoteSnapshot quote, DeliveryQuoteSnapshot.Combo combo) {
        int chunkSize = 5;
        int chunks = Math.max(1, (combo.lines().size() + chunkSize - 1) / chunkSize);
        for (int chunk = 0; chunk < chunks; chunk++) {
            XSLFSlide slide = baseSlide(ppt, combo.name() + (chunk == 0 ? "" : "（续）"), ppt.getSlides().size() + 1);
            int from = chunk * chunkSize;
            int to = Math.min(combo.lines().size(), from + chunkSize);
            if (chunk == 0) {
                addHeroImage(ppt, slide, combo, new Rectangle2D.Double(55, 105, 340, 285));
                box(slide, 55, 408, 340, 56, "图片来源：" + (combo.adoptedImage() == null ? "已确认商品原图拼版" : source(combo.adoptedImage().sourceMode()))
                        + "\n图片仅作搭配示意，以 SKU 明细为准。", 11, false, Color.DARK_GRAY, TextAlign.LEFT);
            }
            List<String[]> rows = new ArrayList<>();
            for (DeliveryQuoteSnapshot.Line line : combo.lines().subList(from, to)) {
                rows.add(new String[] {line.categoryCode(), line.productName(), line.skuCode(),
                        line.colorName() + " / " + line.sizeCode(), Integer.toString(line.qty()), money(line.amount())});
            }
            double x = chunk == 0 ? 420 : 55;
            double width = chunk == 0 ? 485 : 850;
            nativeTable(slide, x, 105, width, Math.min(310, 56 + rows.size() * 50),
                    new String[] {"品类", "商品", "SKU", "颜色/尺码", "数量", "金额"}, rows,
                    chunk == 0 ? new double[] {62, 120, 90, 100, 48, 65}
                            : new double[] {95, 245, 160, 160, 80, 110}, 11);
            box(slide, x, 430, width, 45,
                    "本组合 " + combo.setQty() + " 套  ·  金额 " + money(combo.totalAmount()) + " " + quote.currency(),
                    17, true, BLUE, TextAlign.RIGHT);
        }
    }

    private void detailPages(XMLSlideShow ppt, DeliveryQuoteSnapshot quote) {
        List<String[]> rows = new ArrayList<>();
        for (DeliveryQuoteSnapshot.Combo combo : quote.combos()) {
            for (DeliveryQuoteSnapshot.Line line : combo.lines()) {
                rows.add(new String[] {combo.comboNo(), line.skuCode(), line.productName(), line.sizeCode(),
                        Integer.toString(line.qty()), money(line.quotePrice()), money(line.amount())});
            }
        }
        int chunkSize = 9;
        for (int from = 0; from < rows.size(); from += chunkSize) {
            int to = Math.min(rows.size(), from + chunkSize);
            XSLFSlide slide = baseSlide(ppt, "报价明细" + (from == 0 ? "" : "（续）"), ppt.getSlides().size() + 1);
            nativeTable(slide, 35, 95, 890, 385,
                    new String[] {"组合", "SKU", "商品", "尺码", "数量", "单价", "金额"}, rows.subList(from, to),
                    new double[] {85, 135, 250, 80, 70, 115, 135}, 11);
        }
    }

    /**
     * 三、四品类方案保留商品原图清单与组合图的可追溯关系。相同对象只展示一次，
     * 每页最多八张，避免大量图片把组合正文压缩成不可编辑截图。
     */
    private void sourceImagePages(XMLSlideShow ppt, DeliveryQuoteSnapshot quote) {
        Map<String, SourceImage> images = new LinkedHashMap<>();
        for (DeliveryQuoteSnapshot.Combo combo : quote.combos()) {
            for (DeliveryQuoteSnapshot.Line line : combo.lines()) {
                if (line.imageKey() != null && line.imageHash() != null) {
                    images.putIfAbsent(line.imageKey() + ":" + line.imageHash(),
                            new SourceImage(line.imageKey(), line.imageHash(), line.skuCode(), line.productName()));
                }
            }
        }
        List<SourceImage> values = List.copyOf(images.values());
        int chunkSize = 8;
        for (int from = 0; from < values.size(); from += chunkSize) {
            int to = Math.min(values.size(), from + chunkSize);
            XSLFSlide slide = baseSlide(ppt, "商品原图清单" + (from == 0 ? "" : "（续）"),
                    ppt.getSlides().size() + 1);
            for (int index = from; index < to; index++) {
                int local = index - from;
                int column = local % 4;
                int row = local / 4;
                double x = 45 + column * 225;
                double y = 92 + row * 198;
                SourceImage source = values.get(index);
                embedImage(ppt, slide, source.objectKey(), source.sha256(),
                        new Rectangle2D.Double(x, y, 180, 135));
                box(slide, x, y + 140, 180, 44,
                        source.skuCode() + "\n" + source.productName(), 9, false, INK, TextAlign.CENTER);
            }
        }
    }

    private void terms(XMLSlideShow ppt, DeliveryQuoteSnapshot quote) {
        XSLFSlide slide = baseSlide(ppt, "商务说明", ppt.getSlides().size() + 1);
        String terms = "报价版本：" + quote.quoteNo() + " / V" + quote.versionNo()
                + "\n有效期：" + (quote.validUntil() == null ? "—" : DATE.format(quote.validUntil()))
                + "\n库存核对：" + latestStock(quote)
                + "\n币种与税口径：" + quote.currency() + "，" + tax(quote)
                + "\n优惠：" + discount(quote)
                + "\n运费及附加费用：" + money(quote.freight()) + " " + quote.currency()
                + "\n公开说明：" + (quote.publicNote() == null ? "无" : quote.publicNote())
                + "\n\nAI/拼版图片仅用于方案示意；商品款号、颜色、尺码、数量与金额以本文件可编辑表格为准。";
        box(slide, 80, 105, 800, 330, terms, 17, false, INK, TextAlign.LEFT);
        box(slide, 80, 455, 800, 35, "报价总额  " + money(quote.totalAmount()) + " " + quote.currency(),
                23, true, BLUE, TextAlign.RIGHT);
    }

    private void addHeroImage(XMLSlideShow ppt, XSLFSlide slide, DeliveryQuoteSnapshot.Combo combo,
            Rectangle2D target) {
        DeliveryQuoteSnapshot.ImageRef selected = combo.adoptedImage();
        String key = selected == null && !combo.lines().isEmpty() ? combo.lines().get(0).imageKey() : selected.objectKey();
        String sha = selected == null && !combo.lines().isEmpty() ? combo.lines().get(0).imageHash() : selected.sha256();
        if (key == null || sha == null) {
            placeholder(slide, target, "暂无可交付图片");
            return;
        }
        embedImage(ppt, slide, key, sha, target);
    }

    private void embedImage(XMLSlideShow ppt, XSLFSlide slide, String key, String sha, Rectangle2D target) {
        byte[] bytes = media.readVerified(key, sha);
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new ServiceException("PPT 图片格式不可解析");
            PictureType type = pictureType(bytes);
            XSLFPictureData data = ppt.addPicture(bytes, type);
            XSLFPictureShape picture = slide.createPicture(data);
            picture.setAnchor(contain(target, image.getWidth(), image.getHeight()));
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("PPT 图片嵌入失败");
        }
    }

    private XSLFSlide baseSlide(XMLSlideShow ppt, String title, int page) {
        XSLFSlide slide = ppt.createSlide();
        XSLFAutoShape top = slide.createAutoShape();
        top.setAnchor(new Rectangle2D.Double(0, 0, 960, 18));
        top.setFillColor(BLUE);
        top.setLineColor(BLUE);
        if (title != null) box(slide, 45, 34, 820, 44, title, 25, true, NAVY, TextAlign.LEFT);
        box(slide, 875, 498, 45, 20, Integer.toString(page), 10, false, Color.GRAY, TextAlign.RIGHT);
        return slide;
    }

    private static XSLFTextBox box(XSLFSlide slide, double x, double y, double w, double h,
            String text, double size, boolean bold, Color color, TextAlign align) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle2D.Double(x, y, w, h));
        box.setText(text == null ? "" : text);
        for (XSLFTextParagraph paragraph : box.getTextParagraphs()) {
            paragraph.setTextAlign(align);
            paragraph.setLineSpacing(110d);
            for (XSLFTextRun run : paragraph.getTextRuns()) {
                run.setFontFamily("Microsoft YaHei");
                run.setFontSize(size);
                run.setBold(bold);
                run.setFontColor(color);
            }
        }
        return box;
    }

    private static void nativeTable(XSLFSlide slide, double x, double y, double w, double h,
            String[] headers, List<String[]> rows, double[] widths, double fontSize) {
        XSLFTable table = slide.createTable();
        table.setAnchor(new Rectangle2D.Double(x, y, w, h));
        XSLFTableRow head = table.addRow();
        head.setHeight(32);
        for (int i = 0; i < headers.length; i++) styleCell(head.addCell(), headers[i], true, fontSize);
        for (String[] values : rows) {
            XSLFTableRow row = table.addRow();
            row.setHeight(Math.max(35, Math.min(62, h / Math.max(rows.size() + 1, 1))));
            for (int i = 0; i < headers.length; i++) styleCell(row.addCell(), i < values.length ? values[i] : "", false, fontSize);
        }
        for (int i = 0; i < widths.length; i++) table.setColumnWidth(i, widths[i]);
    }

    private static void styleCell(XSLFTableCell cell, String text, boolean header, double fontSize) {
        cell.setText(text == null ? "" : text);
        cell.setFillColor(header ? NAVY : Color.WHITE);
        cell.setBorderColor(org.apache.poi.sl.usermodel.TableCell.BorderEdge.bottom, new Color(190, 205, 222));
        cell.setBorderWidth(org.apache.poi.sl.usermodel.TableCell.BorderEdge.bottom, 0.7);
        for (XSLFTextParagraph paragraph : cell.getTextParagraphs()) {
            paragraph.setTextAlign(TextAlign.LEFT);
            for (XSLFTextRun run : paragraph.getTextRuns()) {
                run.setFontFamily("Microsoft YaHei");
                run.setFontSize(fontSize);
                run.setBold(header);
                run.setFontColor(header ? Color.WHITE : INK);
            }
        }
    }

    private static void placeholder(XSLFSlide slide, Rectangle2D target, String message) {
        XSLFAutoShape background = slide.createAutoShape();
        background.setAnchor(target);
        background.setFillColor(PALE);
        background.setLineColor(new Color(180, 200, 220));
        box(slide, target.getX() + 20, target.getY() + target.getHeight() / 2 - 20,
                target.getWidth() - 40, 40, message, 15, false, Color.GRAY, TextAlign.CENTER);
    }

    private static Rectangle2D contain(Rectangle2D target, int width, int height) {
        double scale = Math.min(target.getWidth() / width, target.getHeight() / height);
        double w = width * scale, h = height * scale;
        return new Rectangle2D.Double(target.getCenterX() - w / 2, target.getCenterY() - h / 2, w, h);
    }

    private static PictureType pictureType(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50) return PictureType.PNG;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) return PictureType.JPEG;
        throw new ServiceException("PPT 仅嵌入 PNG/JPEG 图片");
    }

    private static String requirement(DeliveryQuoteSnapshot quote, String field) {
        String value = quote.requirement() == null ? null : quote.requirement().path(field).asText(null);
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String money(BigDecimal value) {
        return value == null ? "—" : value.setScale(2).toPlainString();
    }

    private static String perSet(DeliveryQuoteSnapshot.Combo combo) {
        if (combo.totalAmount() == null || combo.setQty() <= 0) return "—";
        return combo.totalAmount().divide(BigDecimal.valueOf(combo.setQty()), 2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String tax(DeliveryQuoteSnapshot quote) {
        return "included".equals(quote.taxMode()) ? "含税" : "未税，税率 " + quote.taxRate().toPlainString() + "%";
    }

    private static String discount(DeliveryQuoteSnapshot quote) {
        if ("fixed".equals(quote.discountType())) return "固定优惠 " + money(quote.fixedDiscount());
        return "优惠率 " + (quote.discountRate() == null ? "0" : quote.discountRate().toPlainString()) + "%";
    }

    private static String latestStock(DeliveryQuoteSnapshot quote) {
        return quote.combos().stream().flatMap(c -> c.lines().stream()).map(DeliveryQuoteSnapshot.Line::stockAsOf)
                .filter(java.util.Objects::nonNull).max(Instant::compareTo).map(DATE::format).orElse("—");
    }

    private static List<String> skuCodes(DeliveryQuoteSnapshot quote) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        quote.combos().forEach(c -> c.lines().forEach(line -> values.add(line.skuCode())));
        return List.copyOf(values);
    }

    private static String source(String mode) {
        return switch (mode == null ? "" : mode) {
            case "provider" -> "已复核 AI 候选";
            case "upload" -> "已复核外部上传";
            case "sample" -> "体验样例";
            case "composition" -> "商品原图拼版";
            case "reuse" -> "已确认历史复用图";
            default -> "已确认方案图片";
        };
    }

    private record SourceImage(String objectKey, String sha256, String skuCode, String productName) {
    }
}
