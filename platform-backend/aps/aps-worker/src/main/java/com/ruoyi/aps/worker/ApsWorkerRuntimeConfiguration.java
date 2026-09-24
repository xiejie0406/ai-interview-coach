package com.ruoyi.aps.worker;

import com.ruoyi.aps.application.foundation.ApsAuditActor;
import com.ruoyi.aps.application.foundation.ApsAuditActorProvider;
import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.planning.PlanRepository;
import com.ruoyi.aps.solver.ortools.CpSatFiniteCapacitySolver;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Worker 运行逻辑的三重安全门。
 *
 * <p>IMP-01 尚未注册轮询器；后续所有轮询、领取和求解 Bean 都必须放在
 * 本配置边界内，同时显式打开 APS 总开关、Worker 开关和轮询开关。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.worker", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.worker", name = "polling-enabled", havingValue = "true")
public class ApsWorkerRuntimeConfiguration
{
    @Bean(name = "apsAuditActorProvider")
    public ApsAuditActorProvider workerAuditActorProvider()
    {
        return () -> ApsAuditActor.system("aps-worker");
    }

    @Bean
    public CpSatFiniteCapacitySolver apsCpSatFiniteCapacitySolver() { return new CpSatFiniteCapacitySolver(); }

    @Bean
    @ConditionalOnBean(PlanRepository.class)
    public ApsWorkerCycle apsWorkerCycle(PlanRepository plans, ApsTransactionOperations transactions,
            CpSatFiniteCapacitySolver solver, ApsWorkerProperties properties)
    {
        return new ApsWorkerCycle(plans, transactions, solver::solve, Clock.systemUTC(),
                Duration.ofMillis(properties.getStaleSolvingTimeoutMs()));
    }

    @Bean
    @ConditionalOnBean(ApsWorkerCycle.class)
    public ApsWorkerPoller apsWorkerPoller(ApsWorkerCycle cycle) { return new ApsWorkerPoller(cycle); }
}
