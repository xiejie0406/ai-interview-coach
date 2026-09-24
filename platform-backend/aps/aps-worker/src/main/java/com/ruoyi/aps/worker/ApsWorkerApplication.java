package com.ruoyi.aps.worker;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.ruoyi.aps.infrastructure.mysql.configuration.ApsDataSourceConfiguration;
import com.ruoyi.aps.infrastructure.mysql.configuration.ApsFlywayConfiguration;
import com.ruoyi.aps.infrastructure.mysql.planning.ApsPlanningPersistenceConfiguration;

/**
 * 生产排程独立 Worker 入口。
 *
 * <p>当前只验证进程边界和配置绑定，不注册轮询器，也不会自动执行求解。</p>
 */
@SpringBootApplication(
        exclude = DataSourceAutoConfiguration.class,
        excludeName = "com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure")
@EnableConfigurationProperties(ApsWorkerProperties.class)
@EnableScheduling
@Import({ApsDataSourceConfiguration.class, ApsFlywayConfiguration.class,
        ApsPlanningPersistenceConfiguration.class})
public class ApsWorkerApplication
{
    public static void main(String[] args)
    {
        new SpringApplicationBuilder(ApsWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
