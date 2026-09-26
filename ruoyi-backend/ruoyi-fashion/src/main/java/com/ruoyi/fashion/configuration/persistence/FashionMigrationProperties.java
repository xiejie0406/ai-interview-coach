package com.ruoyi.fashion.configuration.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Fashion 迁移开关；默认关闭，且目标库名与首次 baseline 必须显式声明。 */
@ConfigurationProperties(prefix = "fashion.migration")
public class FashionMigrationProperties {
    private boolean enabled;
    private String expectedDatabase;
    private boolean baselineApproved;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExpectedDatabase() {
        return expectedDatabase;
    }

    public void setExpectedDatabase(String expectedDatabase) {
        this.expectedDatabase = expectedDatabase;
    }

    public boolean isBaselineApproved() {
        return baselineApproved;
    }

    public void setBaselineApproved(boolean baselineApproved) {
        this.baselineApproved = baselineApproved;
    }
}
