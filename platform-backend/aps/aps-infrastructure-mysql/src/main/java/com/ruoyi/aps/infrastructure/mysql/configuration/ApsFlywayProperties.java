package com.ruoyi.aps.infrastructure.mysql.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aps.flyway")
public class ApsFlywayProperties
{
    private boolean enabled;
    private String[] locations = { "classpath:db/migration/aps" };
    private String table = "aps_flyway_schema_history";

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String[] getLocations()
    {
        return locations.clone();
    }

    public void setLocations(String[] locations)
    {
        this.locations = locations.clone();
    }

    public String getTable()
    {
        return table;
    }

    public void setTable(String table)
    {
        this.table = table;
    }
}
