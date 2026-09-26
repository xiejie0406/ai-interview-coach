package com.ruoyi.aden.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/** 仅访问 Aden 自有采集表；写事务由上层统一管理。 */
public final class AdenCollectionStore {
    private final JdbcTemplate jdbc;
    public AdenCollectionStore(DataSource source) { jdbc = new JdbcTemplate(source); }
    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public List<Map<String,Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    public Map<String,Object> one(String sql, Object... args) {
        List<Map<String,Object>> rows = rows(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
