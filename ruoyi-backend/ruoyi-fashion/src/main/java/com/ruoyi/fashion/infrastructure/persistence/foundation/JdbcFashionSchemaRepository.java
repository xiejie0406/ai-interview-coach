package com.ruoyi.fashion.infrastructure.persistence.foundation;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ruoyi.fashion.application.foundation.port.FashionSchemaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 显式使用 fashionJdbcTemplate 的只读 Schema Adapter。 */
@FashionModuleEnabled
@Repository
public class JdbcFashionSchemaRepository implements FashionSchemaRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFashionSchemaRepository(
            @Qualifier("fashionJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String currentDatabase() {
        return jdbc.getJdbcTemplate().queryForObject("select database()", String.class);
    }

    @Override
    public Set<String> businessTables() {
        String database = currentDatabase();
        List<String> values = jdbc.queryForList("""
                select table_name from information_schema.tables
                 where table_schema = :database and left(table_name, 3) = 'fq_'
                 order by table_name
                """, Map.of("database", database), String.class);
        return new LinkedHashSet<>(values);
    }
}
