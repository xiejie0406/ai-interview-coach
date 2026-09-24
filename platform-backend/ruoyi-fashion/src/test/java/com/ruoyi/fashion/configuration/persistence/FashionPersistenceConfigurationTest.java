package com.ruoyi.fashion.configuration.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class FashionPersistenceConfigurationTest {
    @Test
    void everyFashionPersistenceBeanUsesTheNamedDynamicDataSource() {
        DataSource dynamic = mock(DataSource.class);
        PlatformTransactionManager interview = mock(PlatformTransactionManager.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("dynamicDataSource", dynamic);
            context.getBeanFactory().registerSingleton("interviewTransactionManager", interview);
            context.register(FashionPersistenceConfiguration.class);
            context.refresh();

            NamedParameterJdbcTemplate jdbc = context.getBean(
                    "fashionJdbcTemplate", NamedParameterJdbcTemplate.class);
            DataSourceTransactionManager manager = (DataSourceTransactionManager) context.getBean(
                    "fashionTransactionManager");
            TransactionTemplate transaction = context.getBean(
                    "fashionTransactionTemplate", TransactionTemplate.class);

            assertSame(dynamic, jdbc.getJdbcTemplate().getDataSource());
            assertSame(dynamic, manager.getDataSource());
            assertSame(manager, transaction.getTransactionManager());
            assertSame(interview, context.getBean("interviewTransactionManager"));
            assertFalse(context.containsBean("interviewJdbcTemplate"));
        }
    }

    @Test
    void migrationPropertiesFailClosedByDefault() {
        FashionMigrationProperties properties = new FashionMigrationProperties();
        assertFalse(properties.isEnabled());
        assertFalse(properties.isBaselineApproved());
        assertTrue(properties.getExpectedDatabase() == null || properties.getExpectedDatabase().isBlank());
    }
}
