package com.ruoyi.aps.infrastructure.mysql.resource;

import com.ruoyi.aps.application.foundation.ApsTransactionOperations;
import com.ruoyi.aps.application.resource.ResourceManagementService;
import com.ruoyi.aps.application.resource.ResourceRepository;
import com.ruoyi.aps.infrastructure.mysql.mapper.ApsResourceMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aps", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.persistence", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "aps.datasource", name = "enabled", havingValue = "true")
public class ApsResourcePersistenceConfiguration
{
    @Bean
    public ResourceRepository apsResourceRepository(ApsResourceMapper mapper)
    {
        return new MybatisResourceRepository(mapper);
    }

    @Bean
    public ResourceManagementService apsResourceManagementService(ResourceRepository repository,
            ApsTransactionOperations transactions)
    {
        return new ResourceManagementService(repository, transactions);
    }
}
