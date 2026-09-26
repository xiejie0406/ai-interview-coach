package com.ruoyi.fashion.infrastructure.persistence.operations;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ruoyi.fashion.application.operations.OperationsExceptionItem;
import com.ruoyi.fashion.application.operations.RetentionCandidate;
import com.ruoyi.fashion.application.operations.port.FashionOperationsRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@FashionModuleEnabled
@Repository
public class JdbcFashionOperationsRepository implements FashionOperationsRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFashionOperationsRepository(
            @Qualifier("fashionJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long countImports(Instant since) {
        return count("select count(*) from fq_import_batch where create_time>=:since", Map.of("since", ts(since)));
    }

    @Override
    public long countFailedImports(Instant since) {
        return count("select count(*) from fq_import_batch where create_time>=:since "
                + "and status in ('invalid','failed','conflict')", Map.of("since", ts(since)));
    }

    @Override
    public long countImageFailedOrUnknown() {
        return count("select count(*) from fq_quote_image where status in ('failed','unknown')", Map.of());
    }

    @Override
    public long countExpiredStock(Instant cutoff) {
        return count("select count(*) from fq_stock where as_of<:cutoff", Map.of("cutoff", ts(cutoff)));
    }

    @Override
    public long countDeliveryTasks(Instant since) {
        return count("select count(*) from fq_quote_file where create_time>=:since", Map.of("since", ts(since)));
    }

    @Override
    public long countFailedDeliveryTasks(Instant since) {
        return count("select count(*) from fq_quote_file where create_time>=:since and status='failed'",
                Map.of("since", ts(since)));
    }

    @Override
    public double averageDeliveryDurationMs(Instant since) {
        Double result = jdbc.queryForObject("""
                select coalesce(avg(timestampdiff(microsecond,create_time,update_time)/1000.0),0)
                  from fq_quote_file where create_time>=:since and status='success'
                """, Map.of("since", ts(since)), Double.class);
        return result == null ? 0 : result;
    }

    @Override
    public List<OperationsExceptionItem> exceptions(Instant now, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbc.query("""
                select * from (
                    select 'import' item_type,cast(id as char) item_id,batch_no correlation_id,status,
                           update_time occurred_at,concat('错误行数 ',error_count) error_summary
                      from fq_import_batch where status in ('invalid','failed','conflict')
                    union all
                    select 'image',cast(id as char),request_key,status,update_time,
                           coalesce(nullif(error_message,''),'图片任务待处理')
                      from fq_quote_image where status in ('failed','unknown')
                    union all
                    select 'delivery',cast(id as char),request_key,status,update_time,
                           coalesce(nullif(error_message,''),'文件任务失败')
                      from fq_quote_file where status='failed'
                ) exceptions order by occurred_at desc limit :limit
                """, new MapSqlParameterSource().addValue("limit", safeLimit), this::mapException);
    }

    @Override
    public List<RetentionCandidate> retentionCandidates(
            Instant now, Instant quoteCutoff, Instant importCutoff, Instant failedImageCutoff) {
        List<RetentionCandidate> items = new ArrayList<>();
        items.addAll(jdbc.query("""
                select 'import_file' category,cast(id as char) record_id,file_key object_key,create_time,
                       date_add(create_time,interval timestampdiff(day,:importCutoff,:now) day) eligible_at,
                       case when create_time<:importCutoff then 'cleanable' else 'not_due' end decision,
                       case when create_time<:importCutoff then '原始导入文件超过默认保留期' else '尚未到默认保留期' end reason,
                       null quote_id
                  from fq_import_batch where file_key is not null order by create_time limit 200
                """, new MapSqlParameterSource().addValue("now", ts(now)).addValue("importCutoff", ts(importCutoff)),
                this::mapRetention));
        items.addAll(jdbc.query("""
                select 'failed_image' category,cast(i.id as char) record_id,
                       json_unquote(json_extract(result.value,'$.object_key')) object_key,i.create_time,
                       date_add(i.create_time,interval timestampdiff(day,:failedCutoff,:now) day) eligible_at,
                       case when c.selected_image_id=i.id then 'protected'
                            when i.create_time<:failedCutoff then 'cleanable' else 'not_due' end decision,
                       case when c.selected_image_id=i.id then '被报价组合采用引用保护'
                            when i.create_time<:failedCutoff then '失败未采用素材超过默认保留期'
                            else '尚未到默认保留期' end reason,
                       cast(c.quote_id as char) quote_id
                  from fq_quote_image i join fq_quote_combo c on c.id=i.combo_id
                  join json_table(i.results_json,'$[*]' columns (value json path '$')) result
                 where i.status in ('failed','partial','cancelled','unknown')
                 order by i.create_time limit 200
                """, new MapSqlParameterSource().addValue("now", ts(now)).addValue("failedCutoff", ts(failedImageCutoff)),
                this::mapRetention));
        items.addAll(jdbc.query("""
                select 'quote_file' category,cast(f.id as char) record_id,
                       json_unquote(json_extract(file.value,'$.objectKey')) object_key,f.create_time,
                       date_add(f.create_time,interval timestampdiff(day,:quoteCutoff,:now) day) eligible_at,
                       case when cast(replace(replace(json_unquote(json_extract(file.value,'$.retainUntil')),
                                 'T',' '),'Z','') as datetime(3))>:now then 'extended'
                            when q.valid_until>=:now then 'protected'
                            when f.create_time<:quoteCutoff then 'cleanable' else 'protected' end decision,
                       case when cast(replace(replace(json_unquote(json_extract(file.value,'$.retainUntil')),
                                 'T',' '),'Z','') as datetime(3))>:now then '管理员已延长保留期'
                            when q.valid_until>=:now then '报价仍在有效期内'
                            when f.create_time<:quoteCutoff then '报价交付文件超过默认保留期'
                            else '随确认报价默认保留' end reason,
                       cast(q.id as char) quote_id
                  from fq_quote_file f join fq_quote q on q.id=f.quote_id
                  join json_table(f.files_json,'$[*]' columns (value json path '$')) file
                 where f.status='success' order by f.create_time limit 200
                """, new MapSqlParameterSource().addValue("now", ts(now)).addValue("quoteCutoff", ts(quoteCutoff)),
                this::mapRetention));
        return List.copyOf(items);
    }

    private long count(String sql, Map<String, ?> parameters) {
        Long result = jdbc.queryForObject(sql, parameters, Long.class);
        return result == null ? 0 : result;
    }

    private OperationsExceptionItem mapException(ResultSet rs, int row) throws SQLException {
        return new OperationsExceptionItem(rs.getString("item_type"), rs.getString("item_id"),
                rs.getString("correlation_id"), rs.getString("status"), instant(rs, "occurred_at"),
                safeSummary(rs.getString("error_summary")));
    }

    private RetentionCandidate mapRetention(ResultSet rs, int row) throws SQLException {
        return new RetentionCandidate(rs.getString("category"), rs.getString("record_id"),
                rs.getString("object_key"), instant(rs, "create_time"), instant(rs, "eligible_at"),
                rs.getString("decision"), rs.getString("reason"), rs.getString("quote_id"));
    }

    private static String safeSummary(String source) {
        if (source == null) return "待处理";
        String value = source.replaceAll("(?i)(secret|token|password|cookie|signature)\\s*[:=]\\s*[^\\s,;]+",
                "$1=[REDACTED]").replaceAll("[\\r\\n\\t]+", " ");
        return value.substring(0, Math.min(value.length(), 300));
    }

    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
