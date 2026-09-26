package com.ruoyi.fashion.infrastructure.persistence.importing;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.fashion.application.importing.ImportRowError;
import com.ruoyi.fashion.application.importing.port.FashionImportRepository;
import com.ruoyi.fashion.domain.importing.FashionImportBatch;
import com.ruoyi.fashion.domain.importing.FashionImportDetail;
import com.ruoyi.fashion.infrastructure.persistence.foundation.FashionJsonCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@FashionModuleEnabled
@Repository
public class JdbcFashionImportRepository implements FashionImportRepository {
    private static final String BATCH_COLUMNS = """
            id,batch_no,template_code,template_version,import_type,operation_type,source_batch_id,source_code,
            warehouse_code,file_name,file_key,
            file_hash,mapping_snapshot,scope_json,scope_hash,base_data_hash,as_of,request_key,expected_count,
            actual_count,error_count,status,validated_at,published_at,operator_note,error_message,create_by,create_time,
            update_by,update_time,row_version
            """;
    private static final String DETAIL_COLUMNS = """
            id,batch_id,detail_no,source_row_no,row_type,product_id,business_key,raw_data,normalized_data,before_data,
            after_data,status,error_json,change_type,create_by,create_time,update_by,update_time,row_version
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final FashionJsonCodec json;
    private final ObjectMapper objectMapper;
    private final RowMapper<FashionImportBatch> batchMapper = this::mapBatch;
    private final RowMapper<FashionImportDetail> detailMapper = this::mapDetail;

    public JdbcFashionImportRepository(
            @Qualifier("fashionJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            FashionJsonCodec json,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.json = json;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<FashionImportBatch> findBatchByRequestKey(String requestKey) {
        return jdbc.query("select " + BATCH_COLUMNS + " from fq_import_batch where request_key=:requestKey",
                Map.of("requestKey", requestKey), batchMapper).stream().findFirst();
    }

    @Override
    public Optional<FashionImportBatch> findBatchById(long id) {
        return jdbc.query("select " + BATCH_COLUMNS + " from fq_import_batch where id=:id",
                Map.of("id", id), batchMapper).stream().findFirst();
    }

    @Override
    public List<FashionImportDetail> findDetails(long batchId) {
        return jdbc.query("select " + DETAIL_COLUMNS
                + " from fq_import_detail where batch_id=:batchId order by detail_no",
                Map.of("batchId", batchId), detailMapper);
    }

    @Override
    public void insertBatch(FashionImportBatch b) {
        int changed = jdbc.update("""
                insert into fq_import_batch (
                    id,batch_no,template_code,template_version,import_type,operation_type,source_batch_id,source_code,
                    warehouse_code,file_name,
                    file_key,file_hash,mapping_snapshot,scope_json,scope_hash,base_data_hash,as_of,request_key,
                    expected_count,actual_count,error_count,status,validated_at,published_at,operator_note,error_message,
                    create_by,create_time,update_by,update_time,row_version)
                values (
                    :id,:batchNo,:templateCode,:templateVersion,:importType,:operationType,:sourceBatchId,:sourceCode,
                    :warehouseCode,:fileName,
                    :fileKey,:fileHash,:mapping,:scope,:scopeHash,:baseDataHash,:asOf,:requestKey,:expectedCount,
                    :actualCount,:errorCount,:status,:validatedAt,:publishedAt,:operatorNote,:errorMessage,
                    :createBy,:createTime,:updateBy,:updateTime,:rowVersion)
                """, batchParameters(b));
        if (changed != 1) {
            throw new IllegalStateException("导入批次未写入一行");
        }
    }

    @Override
    public void insertDetails(List<FashionImportDetail> details) {
        SqlParameterSource[] parameters = details.stream().map(this::detailParameters).toArray(SqlParameterSource[]::new);
        int[] changed = jdbc.batchUpdate("""
                insert into fq_import_detail (
                    id,batch_id,detail_no,source_row_no,row_type,product_id,business_key,raw_data,normalized_data,
                    before_data,after_data,status,error_json,change_type,create_by,create_time,update_by,update_time,row_version)
                values (
                    :id,:batchId,:detailNo,:sourceRowNo,:rowType,:productId,:businessKey,:raw,:normalized,
                    :before,:after,:status,:errors,:changeType,:createBy,:createTime,:updateBy,:updateTime,:rowVersion)
                """, parameters);
        if (changed.length != details.size()
                || java.util.Arrays.stream(changed).anyMatch(count -> count != 1 && count != java.sql.Statement.SUCCESS_NO_INFO)) {
            throw new IllegalStateException("导入明细写入数量不一致");
        }
    }

    @Override
    public boolean markPublishing(long batchId, long expectedVersion, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_import_batch set status='publishing',update_by=:operator,update_time=:now,
                    row_version=row_version+1
                 where id=:id and status='validated' and row_version=:version
                """, new MapSqlParameterSource().addValue("operator", operatorId)
                .addValue("now", Timestamp.from(now)).addValue("id", batchId)
                .addValue("version", expectedVersion)) == 1;
    }

    @Override
    public void markDetailApplied(
            long detailId, Long productId, Map<String, Object> afterData, long operatorId, Instant now) {
        int changed = jdbc.update("""
                update fq_import_detail set product_id=:productId,after_data=:after,status='applied',
                    update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and status='valid'
                """, new MapSqlParameterSource().addValue("productId", productId)
                .addValue("after", json.write(afterData)).addValue("operator", operatorId)
                .addValue("now", Timestamp.from(now)).addValue("id", detailId));
        if (changed != 1) {
            throw new IllegalStateException("导入明细状态已变化");
        }
    }

    @Override
    public void markBatchSuccess(long batchId, long operatorId, Instant now) {
        int changed = jdbc.update("""
                update fq_import_batch set status='success',published_at=:now,update_by=:operator,
                    update_time=:now,row_version=row_version+1
                 where id=:id and status='publishing'
                """, Map.of("id", batchId, "operator", operatorId, "now", Timestamp.from(now)));
        if (changed != 1) {
            throw new IllegalStateException("导入批次无法完成");
        }
    }

    @Override
    public void markBatchConflict(long batchId, long operatorId, Instant now, String reason) {
        jdbc.update("""
                update fq_import_batch set status='conflict',error_message=:reason,update_by=:operator,
                    update_time=:now,row_version=row_version+1
                 where id=:id and status in ('validated','publishing')
                """, Map.of("id", batchId, "operator", operatorId, "now", Timestamp.from(now), "reason", reason));
    }

    private MapSqlParameterSource batchParameters(FashionImportBatch b) {
        return new MapSqlParameterSource()
                .addValue("id", b.id()).addValue("batchNo", b.batchNo()).addValue("templateCode", b.templateCode())
                .addValue("templateVersion", b.templateVersion()).addValue("importType", b.importType())
                .addValue("operationType", b.operationType()).addValue("sourceBatchId", b.sourceBatchId())
                .addValue("sourceCode", b.sourceCode()).addValue("warehouseCode", b.warehouseCode())
                .addValue("fileName", b.fileName()).addValue("fileKey", b.fileKey()).addValue("fileHash", b.fileHash())
                .addValue("mapping", json.write(b.mappingSnapshot())).addValue("scope", json.write(b.scope()))
                .addValue("scopeHash", b.scopeHash()).addValue("baseDataHash", b.baseDataHash())
                .addValue("asOf", Timestamp.from(b.asOf())).addValue("requestKey", b.requestKey())
                .addValue("expectedCount", b.expectedCount()).addValue("actualCount", b.actualCount())
                .addValue("errorCount", b.errorCount()).addValue("status", b.status())
                .addValue("validatedAt", timestamp(b.validatedAt())).addValue("publishedAt", timestamp(b.publishedAt()))
                .addValue("operatorNote", b.operatorNote()).addValue("errorMessage", b.errorMessage())
                .addValue("createBy", b.createBy())
                .addValue("createTime", Timestamp.from(b.createTime())).addValue("updateBy", b.updateBy())
                .addValue("updateTime", Timestamp.from(b.updateTime())).addValue("rowVersion", b.rowVersion());
    }

    private MapSqlParameterSource detailParameters(FashionImportDetail d) {
        return new MapSqlParameterSource()
                .addValue("id", d.id()).addValue("batchId", d.batchId()).addValue("detailNo", d.detailNo())
                .addValue("sourceRowNo", d.sourceRowNo()).addValue("rowType", d.rowType())
                .addValue("productId", d.productId())
                .addValue("businessKey", d.businessKey()).addValue("raw", jsonOrNull(d.rawData()))
                .addValue("normalized", jsonOrNull(d.normalizedData())).addValue("before", jsonOrNull(d.beforeData()))
                .addValue("after", jsonOrNull(d.afterData())).addValue("status", d.status())
                .addValue("errors", json.write(d.errors())).addValue("changeType", d.changeType())
                .addValue("createBy", d.createBy()).addValue("createTime", Timestamp.from(d.createTime()))
                .addValue("updateBy", d.updateBy()).addValue("updateTime", Timestamp.from(d.updateTime()))
                .addValue("rowVersion", d.rowVersion());
    }

    private FashionImportBatch mapBatch(ResultSet rs, int rowNum) throws SQLException {
        return new FashionImportBatch(
                rs.getLong("id"), rs.getString("batch_no"), rs.getString("template_code"),
                rs.getString("template_version"), rs.getString("import_type"), rs.getString("operation_type"),
                rs.getString("source_code"), rs.getString("file_name"), rs.getString("file_key"),
                rs.getString("file_hash"), read(rs.getString("mapping_snapshot"), new TypeReference<Map<String, String>>() {}),
                read(rs.getString("scope_json"), new TypeReference<Map<String, Object>>() {}),
                rs.getString("scope_hash"), rs.getString("base_data_hash"), instant(rs, "as_of"),
                rs.getString("request_key"), rs.getInt("expected_count"), rs.getInt("actual_count"),
                rs.getInt("error_count"), rs.getString("status"), instant(rs, "validated_at"),
                instant(rs, "published_at"), rs.getString("error_message"), rs.getLong("create_by"),
                instant(rs, "create_time"), rs.getLong("update_by"), instant(rs, "update_time"),
                rs.getLong("row_version"), nullableLong(rs, "source_batch_id"), rs.getString("warehouse_code"),
                rs.getString("operator_note"));
    }

    private FashionImportDetail mapDetail(ResultSet rs, int rowNum) throws SQLException {
        return new FashionImportDetail(
                rs.getLong("id"), rs.getLong("batch_id"), rs.getInt("detail_no"),
                nullableInteger(rs, "source_row_no"), nullableLong(rs, "product_id"), rs.getString("business_key"),
                readNullableMap(rs.getString("raw_data")), readNullableMap(rs.getString("normalized_data")),
                readNullableMap(rs.getString("before_data")), readNullableMap(rs.getString("after_data")),
                rs.getString("status"),
                read(rs.getString("error_json"), new TypeReference<List<ImportRowError>>() {}),
                rs.getString("change_type"), rs.getLong("create_by"), instant(rs, "create_time"),
                rs.getLong("update_by"), instant(rs, "update_time"), rs.getLong("row_version"),
                rs.getString("row_type"));
    }

    private String jsonOrNull(Object value) {
        return value == null ? null : json.write(value);
    }

    private Map<String, Object> readNullableMap(String value) {
        return value == null ? null : read(value, new TypeReference<Map<String, Object>>() {});
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("导入 JSON 数据无效", exception);
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
