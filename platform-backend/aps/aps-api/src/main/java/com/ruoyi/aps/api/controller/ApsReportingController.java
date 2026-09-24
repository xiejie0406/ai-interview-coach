package com.ruoyi.aps.api.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import com.ruoyi.aps.application.foundation.ApsAuditActor;
import com.ruoyi.aps.application.foundation.ApsAuditActorProvider;
import com.ruoyi.aps.application.reporting.ReportingCatalog.DailyProductionReport;
import com.ruoyi.aps.application.reporting.ReportingCatalog.LaborCapacityReport;
import com.ruoyi.aps.application.reporting.ReportingCatalog.OrderDeliveryReport;
import com.ruoyi.aps.application.reporting.ReportingService;
import com.ruoyi.aps.application.resource.ResourceAccessScope;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** IMP-10 日报、累计人力产能与订单交期查询/导出。 */
@Validated
@RestController
@RequestMapping("/api/aps/v1/reports")
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.api", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.persistence", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.reporting", name = "enabled", havingValue = "true")
public class ApsReportingController
{
    private static final String UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
    private final ReportingService service;
    private final ApsAuditActorProvider actors;

    public ApsReportingController(ReportingService service, ApsAuditActorProvider actors)
    {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping("/daily-production")
    @PreAuthorize("@ss.hasPermi('aps:report:view')")
    public DailyProductionReport dailyProduction(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(required = false) @Pattern(regexp = UUID) String workshopId,
            @RequestParam(required = false) @Pattern(regexp = UUID) String workCenterId)
    {
        return service.dailyProduction(scope(), businessDate, workshopId, workCenterId);
    }

    @GetMapping("/labor-capacity")
    @PreAuthorize("@ss.hasPermi('aps:report:view')")
    public LaborCapacityReport laborCapacity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) @Pattern(regexp = UUID) String workshopId,
            @RequestParam(required = false) @Pattern(regexp = UUID) String workCenterId,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,64}$") String skillCode)
    {
        return service.laborCapacity(scope(), fromDate, toDate, workshopId, workCenterId, skillCode);
    }

    @GetMapping("/order-delivery")
    @PreAuthorize("@ss.hasPermi('aps:report:view')")
    public OrderDeliveryReport orderDelivery(
            @RequestParam(required = false) @Pattern(regexp = UUID) String orderId)
    {
        return service.orderDelivery(scope(), orderId);
    }

    @Log(title = "APS 车间日报导出", businessType = BusinessType.EXPORT)
    @GetMapping(value = "/daily-production.csv", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("@ss.hasPermi('aps:report:export')")
    public ResponseEntity<byte[]> exportDailyProduction(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(required = false) @Pattern(regexp = UUID) String workshopId,
            @RequestParam(required = false) @Pattern(regexp = UUID) String workCenterId)
    {
        DailyProductionReport report = service.dailyProduction(scope(), businessDate, workshopId, workCenterId);
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("站点", "时区", "业务日期", "车间", "工作中心", "订单", "产品", "任务", "单位",
                "冻结计划量", "当前计划量", "实际加工量", "实际合格量", "报废量", "冻结人时", "当前人时",
                "实际人时", "冻结机时", "当前机时", "实际机时", "计划开始", "计划结束", "承诺时间", "原因码"));
        report.rows().forEach(row -> rows.add(List.of(report.metadata().siteCode(), report.metadata().zoneId(),
                report.businessDate(), value(row.workshopName()), value(row.workCenterName()), row.orderNo(),
                row.itemCode() + " " + row.itemName(), row.taskCode() + " " + row.taskName(), row.uomCode(),
                row.baselinePlannedQty(), row.currentPlannedQty(), row.actualProcessedQty(), row.actualGoodQty(),
                row.scrapQty(), row.baselinePersonHours(), row.currentPersonHours(), row.actualPersonHours(),
                row.baselineMachineHours(), row.currentMachineHours(), row.actualMachineHours(),
                zoned(row.currentStartAt(), report.metadata().zoneId()),
                zoned(row.currentEndAt(), report.metadata().zoneId()),
                zoned(row.promisedAt(), report.metadata().zoneId()), String.join("|", row.reasonCodes()))));
        return csv("aps-daily-production-" + businessDate + ".csv", rows);
    }

    @Log(title = "APS 人力产能导出", businessType = BusinessType.EXPORT)
    @GetMapping(value = "/labor-capacity.csv", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("@ss.hasPermi('aps:report:export')")
    public ResponseEntity<byte[]> exportLaborCapacity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) @Pattern(regexp = UUID) String workshopId,
            @RequestParam(required = false) @Pattern(regexp = UUID) String workCenterId,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,64}$") String skillCode)
    {
        LaborCapacityReport report = service.laborCapacity(scope(), fromDate, toDate, workshopId, workCenterId,
                skillCode);
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("类型", "站点", "时区", "车间/技能", "工作中心/峰值时刻", "人员", "技能",
                "可用/潜力人时", "已排人时", "剩余/待排人时", "峰值需用人数", "峰值合格人数", "峰值缺口", "说明"));
        report.people().forEach(row -> rows.add(List.of("PERSON", report.metadata().siteCode(),
                report.metadata().zoneId(), row.workshopName(), value(row.workCenterName()), row.resourceName(),
                String.join("|", row.skillCodes()), row.availableHours(), row.scheduledHours(), row.remainingHours(),
                "", "", "", row.overloaded() ? "OVERLOADED" : "")));
        report.skills().forEach(row -> rows.add(List.of("SKILL", report.metadata().siteCode(),
                report.metadata().zoneId(), row.skillCode(), zoned(row.peakAt(), report.metadata().zoneId()), "",
                row.skillCode(), row.availablePotentialHours(), row.scheduledHours(), row.unplannedRequiredHours(),
                row.peakRequiredPeople(), row.peakQualifiedPeople(), row.peakShortagePeople(),
                String.join("|", row.reasonCodes()))));
        return csv("aps-labor-capacity-" + fromDate + "-" + toDate + ".csv", rows);
    }

    @Log(title = "APS 订单交期导出", businessType = BusinessType.EXPORT)
    @GetMapping(value = "/order-delivery.csv", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("@ss.hasPermi('aps:report:export')")
    public ResponseEntity<byte[]> exportOrderDelivery(
            @RequestParam(required = false) @Pattern(regexp = UUID) String orderId)
    {
        OrderDeliveryReport report = service.orderDelivery(scope(), orderId);
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("站点", "时区", "订单", "订单状态", "产品行", "产品", "需求量", "单位", "末端放行量",
                "ETA 状态", "预计生产完成", "已知下界", "承诺时间", "原因码"));
        report.orders().forEach(order -> order.lines().forEach(line -> rows.add(List.of(
                report.metadata().siteCode(), report.metadata().zoneId(), order.orderNo(), order.orderStatus(),
                line.lineNo(), line.itemCode() + " " + line.itemName(), line.demandQty(), line.uomCode(),
                line.releasedTerminalQty(), line.etaState(), zoned(line.expectedProductionAt(), report.metadata().zoneId()),
                zoned(line.knownLowerBoundAt(), report.metadata().zoneId()),
                zoned(line.promisedAt(), report.metadata().zoneId()),
                line.reasons().stream().map(value -> value.code()).distinct().reduce((a, b) -> a + "|" + b).orElse("")))));
        return csv("aps-order-delivery.csv", rows);
    }

    private ResourceAccessScope scope()
    {
        ApsAuditActor actor = actors.currentActor();
        return new ResourceAccessScope(actor.userId(),
                SecurityUtils.isAdmin() || SecurityUtils.hasPermi("aps:scope:manage"));
    }

    private ResponseEntity<byte[]> csv(String filename, List<List<?>> rows)
    {
        StringBuilder content = new StringBuilder("\uFEFF");
        for (List<?> row : rows)
        {
            for (int i = 0; i < row.size(); i++)
            {
                if (i > 0) content.append(',');
                content.append(csvCell(row.get(i)));
            }
            content.append("\r\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csvCell(Object raw)
    {
        String value = raw == null ? "" : raw instanceof BigDecimal decimal
                ? decimal.stripTrailingZeros().toPlainString() : String.valueOf(raw);
        String leadingTrimmed = value.stripLeading();
        if (!leadingTrimmed.isEmpty() && "=+-@".indexOf(leadingTrimmed.charAt(0)) >= 0) value = "'" + value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private String zoned(Instant value, String zone)
    {
        return value == null ? "" : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value.atZone(ZoneId.of(zone)));
    }

    private String value(String value) { return value == null ? "" : value; }
}
