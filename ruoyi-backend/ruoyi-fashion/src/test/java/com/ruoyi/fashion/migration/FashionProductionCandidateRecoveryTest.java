package com.ruoyi.fashion.migration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 容量实例重启后的只读恢复核对；由隔离恢复脚本在 mysqld 重启后执行。 */
@EnabledIfSystemProperty(named = "fashion.recovery.mysql.url", matches = "jdbc:mysql:.*")
class FashionProductionCandidateRecoveryTest {
    @Test
    void restartPreservesSchemaCurrentProductsAndImportEvidence() {
        DataSource dataSource = new DriverManagerDataSource(
                System.getProperty("fashion.recovery.mysql.url"),
                System.getProperty("fashion.recovery.mysql.username", "root"),
                System.getProperty("fashion.recovery.mysql.password", ""));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema=database() and left(table_name,3)='fq_'", Integer.class)).isEqualTo(16);
        assertThat(jdbc.queryForObject("select count(*) from fq_product where source_code='CAPACITY'", Integer.class))
                .isEqualTo(100_000);
        assertThat(jdbc.queryForObject("select count(*) from fq_import_detail where batch_id=7800000001", Integer.class))
                .isEqualTo(100_000);
        assertThat(jdbc.queryForObject("select expected_count from fq_import_batch where id=7800000001", Integer.class))
                .isEqualTo(100_000);
        assertThat(jdbc.queryForObject("select error_count from fq_import_batch where id=7800000001", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from fq_product where source_code='CAPACITY' "
                + "and main_image_key is not null", Integer.class)).isEqualTo(40);
    }
}
