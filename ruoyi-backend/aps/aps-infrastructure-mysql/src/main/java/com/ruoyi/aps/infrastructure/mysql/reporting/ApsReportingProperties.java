package com.ruoyi.aps.infrastructure.mysql.reporting;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 单站点报表元数据与业务日期边界。 */
@ConfigurationProperties(prefix = "aps.site")
public class ApsReportingProperties
{
    private String code;
    private String zoneId = "Asia/Shanghai";

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public void validate()
    {
        if (code == null || code.isBlank()) throw new IllegalStateException("启用 APS 报表时必须配置 APS_SITE_CODE");
        if (code.length() > 64) throw new IllegalStateException("APS_SITE_CODE 不能超过 64 字符");
        ZoneId.of(zoneId);
    }

    public ZoneId parsedZoneId() { return ZoneId.of(zoneId); }
}
