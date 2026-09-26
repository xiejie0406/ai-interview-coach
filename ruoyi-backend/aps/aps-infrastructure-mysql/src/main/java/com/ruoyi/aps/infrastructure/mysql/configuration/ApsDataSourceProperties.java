package com.ruoyi.aps.infrastructure.mysql.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * APS 独立 MySQL 数据源配置。URL 必须显式给出，禁止回退到 RuoYi 主库。
 */
@ConfigurationProperties(prefix = "aps.datasource")
public class ApsDataSourceProperties
{
    private boolean enabled;
    private String url;
    private String username;
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
    private int connectTimeoutMs = 5000;
    private int socketTimeoutMs = 30000;
    private int initialSize;
    private int minIdle;
    private int maxActive = 8;

    public void validate()
    {
        if (!enabled)
        {
            throw new IllegalStateException("APS 数据源未启用");
        }
        if (url == null || url.isBlank())
        {
            throw new IllegalStateException("APS_DB_URL 必须显式配置，禁止回退到 RuoYi 主数据源");
        }
        if (!url.startsWith("jdbc:mysql:"))
        {
            throw new IllegalStateException("APS 数据源只允许 jdbc:mysql URL");
        }
        if (username == null || username.isBlank())
        {
            throw new IllegalStateException("APS_DB_USERNAME 必须显式配置");
        }
        if (connectTimeoutMs < 1 || socketTimeoutMs < 1 || initialSize < 0 || minIdle < 0 || maxActive < 1)
        {
            throw new IllegalStateException("APS 数据源连接池参数无效");
        }
        if (minIdle > maxActive || initialSize > maxActive)
        {
            throw new IllegalStateException("APS 数据源 initial-size/min-idle 不能超过 max-active");
        }
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getDriverClassName()
    {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName)
    {
        this.driverClassName = driverClassName;
    }

    public int getConnectTimeoutMs()
    {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs)
    {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getSocketTimeoutMs()
    {
        return socketTimeoutMs;
    }

    public void setSocketTimeoutMs(int socketTimeoutMs)
    {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public int getInitialSize()
    {
        return initialSize;
    }

    public void setInitialSize(int initialSize)
    {
        this.initialSize = initialSize;
    }

    public int getMinIdle()
    {
        return minIdle;
    }

    public void setMinIdle(int minIdle)
    {
        this.minIdle = minIdle;
    }

    public int getMaxActive()
    {
        return maxActive;
    }

    public void setMaxActive(int maxActive)
    {
        this.maxActive = maxActive;
    }
}
