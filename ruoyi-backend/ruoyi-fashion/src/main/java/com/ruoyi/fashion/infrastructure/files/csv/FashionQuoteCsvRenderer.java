package com.ruoyi.fashion.infrastructure.files.csv;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.delivery.DeliveryQuoteSnapshot;
import com.ruoyi.fashion.application.delivery.GeneratedDeliveryArtifact;
import com.ruoyi.fashion.application.delivery.port.FashionDeliveryRenderer;
import com.ruoyi.fashion.infrastructure.files.DeliveryFileNames;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

@FashionModuleEnabled
@Component
public final class FashionQuoteCsvRenderer implements FashionDeliveryRenderer {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public String fileType() { return "csv"; }

    @Override
    public List<GeneratedDeliveryArtifact> render(DeliveryQuoteSnapshot snapshot) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(0xEF); output.write(0xBB); output.write(0xBF);
            try (CSVPrinter csv = new CSVPrinter(new OutputStreamWriter(output, StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.builder().setHeader("方案号", "版本", "客户", "组合", "品类", "SKU", "款号",
                            "商品名称", "颜色", "尺码", "数量", "单位", "销售单价", "金额", "币种", "税口径",
                            "库存核对时间").get())) {
                for (DeliveryQuoteSnapshot.Combo combo : snapshot.combos()) {
                    for (DeliveryQuoteSnapshot.Line line : combo.lines()) {
                        csv.printRecord(safe(snapshot.quoteNo()), snapshot.versionNo(), safe(snapshot.customerName()),
                                safe(combo.name()), safe(line.categoryCode()), safe(line.skuCode()), safe(line.styleCode()),
                                safe(line.productName()), safe(line.colorName()), safe(line.sizeCode()), line.qty(),
                                safe(line.unit()), money(line.quotePrice()), money(line.amount()), snapshot.currency(),
                                tax(snapshot.taxMode()), line.stockAsOf() == null ? "" : DATE_TIME.format(line.stockAsOf()));
                    }
                }
                csv.printRecord("汇总", "", "", "", "", "", "", "商品小计", "", "", "", "",
                        "", money(snapshot.subtotal()), snapshot.currency(), tax(snapshot.taxMode()), "");
                csv.printRecord("汇总", "", "", "", "", "", "", "优惠", "", "", "", "",
                        "", money(snapshot.discountAmount()), snapshot.currency(), tax(snapshot.taxMode()), "");
                csv.printRecord("汇总", "", "", "", "", "", "", "税额", "", "", "", "",
                        "", money(snapshot.taxAmount()), snapshot.currency(), tax(snapshot.taxMode()), "");
                csv.printRecord("汇总", "", "", "", "", "", "", "最终应付", "", "", "", "",
                        "", money(snapshot.totalAmount()), snapshot.currency(), tax(snapshot.taxMode()), "");
            }
            String fileName = DeliveryFileNames.base(snapshot) + "_报价明细.csv";
            return List.of(new GeneratedDeliveryArtifact(fileName, "text/csv;charset=UTF-8", output.toByteArray(),
                    null, "quote-detail", List.of(), "frozen-quote", "confirmed"));
        } catch (Exception exception) {
            throw new ServiceException("CSV 生成失败");
        }
    }

    /** 防止 Excel/WPS 把客户可控文本解释为公式。 */
    static String safe(String value) {
        if (value == null) return "";
        String normalized = value.replace('\u0000', ' ').replace('\r', ' ').replace('\n', ' ');
        String leftTrimmed = normalized.stripLeading();
        if (!leftTrimmed.isEmpty() && "=+-@".indexOf(leftTrimmed.charAt(0)) >= 0) return "'" + normalized;
        return normalized;
    }

    private static String money(java.math.BigDecimal value) {
        return value == null ? "" : value.setScale(2).toPlainString();
    }

    private static String tax(String mode) {
        return "included".equals(mode) ? "含税" : "未税";
    }
}
