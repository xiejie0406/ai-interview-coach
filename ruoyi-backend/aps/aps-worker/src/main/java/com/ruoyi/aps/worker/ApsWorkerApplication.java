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
import com.ruoyi.aps.infrastructure.mysql.execution.ApsExecutionPersistenceConfiguration;
import com.ruoyi.aps.infrastructure.mysql.planning.ApsPlanningPersistenceConfiguration;
import com.ruoyi.aps.infrastructure.mysql.resource.ApsResourcePersistenceConfiguration;
import com.ruoyi.aps.infrastructure.mysql.routing.ApsManufacturingPersistenceConfiguration;

/**
 * 生产排程独立 Worker 入口。
 *
 * <p>只有 APS 总开关、Worker 开关与轮询开关同时启用时才领取和求解计划。</p>
 */
@SpringBootApplication(
        exclude = DataSourceAutoConfiguration.class,
        excludeName = "com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure")
@EnableConfigurationProperties(ApsWorkerProperties.class)
@EnableScheduling
@Import({ApsDataSourceConfiguration.class, ApsFlywayConfiguration.class,
        ApsResourcePersistenceConfiguration.class, ApsManufacturingPersistenceConfiguration.class,
        ApsExecutionPersistenceConfiguration.class, ApsPlanningPersistenceConfiguration.class})
public class ApsWorkerApplication
{
    public static void main(String[] args)
    {
        new SpringApplicationBuilder(ApsWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
