package com.ruoyi.fashion.infrastructure.persistence.delivery;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.fashion.application.delivery.DeliveryArtifact;
import com.ruoyi.fashion.application.delivery.DeliveryFileTask;
import com.ruoyi.fashion.application.delivery.DeliveryQuoteSnapshot;
import com.ruoyi.fashion.application.delivery.port.FashionDeliveryRepository;
import com.ruoyi.fashion.infrastructure.persistence.foundation.FashionJsonCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@FashionModuleEnabled
@Repository
public class JdbcFashionDeliveryRepository implements FashionDeliveryRepository {
    private static final String TASK_COLUMNS = "f.id,f.quote_id,q.quote_no,q.version_no,q.title quote_title,"
            + "f.file_type,f.purpose,f.quote_hash,f.renderer_version,f.request_key,f.status,f.files_json,"
            + "f.retry_count,f.next_retry_at,f.lease_until,f.error_message,f.last_download_at,f.download_count,"
            + "f.create_by,f.create_time,f.row_version";

    private final NamedParameterJdbcTemplate jdbc;
    private final FashionJsonCodec json;
    private final ObjectMapper mapper;

    public JdbcFashionDeliveryRepository(
            @Qualifier("fashionJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            FashionJsonCodec json, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.json = json;
        this.mapper = mapper;
    }

    @Override
    public Optional<DeliveryQuoteSnapshot> findConfirmedSnapshot(long quoteId) {
        List<DeliveryQuoteSnapshot> headers = jdbc.query("""
                select id,quote_no,version_no,title,customer_name,requested_qty,quote_mode,warehouse_code,currency,
                       tax_mode,tax_rate,discount_type,discount_rate,fixed_discount,freight,subtotal,discount_amount,
                       tax_amount,total_amount,valid_days,valid_until,public_note,content_hash,confirmed_at,
                       requirement_json,presentation_json
                  from fq_quote where id=:id and status='confirmed' and content_hash is not null
                """, Map.of("id", quoteId), (rs, row) -> mapSnapshotHeader(rs));
        if (headers.isEmpty()) return Optional.empty();
        DeliveryQuoteSnapshot header = headers.get(0);
        List<DeliveryQuoteSnapshot.Combo> combos = jdbc.query("""
                select c.id,c.combo_no,c.name,c.category_count,c.set_qty,c.subtotal,c.discount_amount,c.freight,
                       c.tax_amount,c.total_amount,c.selected_image_no,i.source_mode,i.results_json
                  from fq_quote_combo c
                  left join fq_quote_image i on i.id=c.selected_image_id and i.combo_id=c.id
                 where c.quote_id=:quote and c.selected=1 order by c.sort_no,c.id
                """, Map.of("quote", quoteId), (rs, row) -> mapCombo(rs));
        List<DeliveryQuoteSnapshot.Combo> populated = new ArrayList<>();
        for (DeliveryQuoteSnapshot.Combo combo : combos) {
            populated.add(new DeliveryQuoteSnapshot.Combo(combo.id(), combo.comboNo(), combo.name(),
                    combo.categoryCount(), combo.setQty(), combo.subtotal(), combo.discountAmount(), combo.freight(),
                    combo.taxAmount(), combo.totalAmount(), combo.adoptedImage(), findLines(combo.id())));
        }
        return Optional.of(new DeliveryQuoteSnapshot(header.quoteId(), header.quoteNo(), header.versionNo(),
                header.title(), header.customerName(), header.requestedQty(), header.quoteMode(),
                header.warehouseCode(), header.currency(), header.taxMode(), header.taxRate(),
                header.discountType(), header.discountRate(), header.fixedDiscount(), header.freight(),
                header.subtotal(), header.discountAmount(), header.taxAmount(), header.totalAmount(),
                header.validDays(), header.validUntil(), header.publicNote(), header.contentHash(),
                header.confirmedAt(), header.requirement(), header.presentation(), List.copyOf(populated)));
    }

    @Override
    public List<DeliveryFileTask> findByQuote(long quoteId) {
        return jdbc.query("select " + TASK_COLUMNS + " from fq_quote_file f join fq_quote q on q.id=f.quote_id "
                + "where f.quote_id=:quote order by f.create_time desc,f.id desc", Map.of("quote", quoteId),
                this::mapTask);
    }

    @Override
    public Optional<DeliveryFileTask> findById(long id) {
        return first(jdbc.query("select " + TASK_COLUMNS + " from fq_quote_file f join fq_quote q on q.id=f.quote_id "
                + "where f.id=:id", Map.of("id", id), this::mapTask));
    }

    @Override
    public Optional<DeliveryFileTask> findByRequestKey(String requestKey) {
        return first(jdbc.query("select " + TASK_COLUMNS + " from fq_quote_file f join fq_quote q on q.id=f.quote_id "
                + "where f.request_key=:key", Map.of("key", requestKey), this::mapTask));
    }

    @Override
    public boolean insert(DeliveryFileTask task) {
        return jdbc.update("""
                insert into fq_quote_file (id,quote_id,file_type,purpose,quote_hash,renderer_version,request_key,status,
                    files_json,retry_count,next_retry_at,lease_until,error_message,last_download_at,download_count,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:quote,:type,:purpose,:hash,:renderer,:request,'queued',json_array(),0,null,null,null,null,0,
                    :operator,:now,:operator,:now,1)
                """, new MapSqlParameterSource().addValue("id", task.id()).addValue("quote", task.quoteId())
                .addValue("type", task.fileType()).addValue("purpose", task.purpose())
                .addValue("hash", task.quoteHash()).addValue("renderer", task.rendererVersion())
                .addValue("request", task.requestKey()).addValue("operator", task.createBy())
                .addValue("now", Timestamp.from(task.createTime()))) == 1;
    }

    @Override
    public Optional<DeliveryFileTask> claimNext(Instant now, Instant leaseUntil) {
        List<Long> ids = jdbc.query("""
                select id from fq_quote_file
                 where (status='queued' or (status='failed' and retry_count<2 and next_retry_at<=:now)
                        or (status='running' and lease_until<:now))
                 order by create_time,id limit 1 for update skip locked
                """, Map.of("now", Timestamp.from(now)), (rs, row) -> rs.getLong(1));
        if (ids.isEmpty()) return Optional.empty();
        long id = ids.get(0);
        int changed = jdbc.update("""
                update fq_quote_file set status='running',lease_until=:lease,next_retry_at=null,error_message=null,
                    update_by=0,update_time=:now,row_version=row_version+1 where id=:id
                """, new MapSqlParameterSource().addValue("lease", Timestamp.from(leaseUntil))
                .addValue("now", Timestamp.from(now)).addValue("id", id));
        return changed == 1 ? findById(id) : Optional.empty();
    }

    @Override
    public boolean complete(long id, long expectedRowVersion, List<DeliveryArtifact> artifacts,
            long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote_file set status='success',files_json=:files,next_retry_at=null,lease_until=null,
                    error_message=null,update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and status='running' and row_version=:version and lease_until>=:now
                """, taskUpdate(id, expectedRowVersion, operatorId, now)
                .addValue("files", json.write(artifacts))) == 1;
    }

    @Override
    public boolean fail(long id, long expectedRowVersion, int retryCount, Instant nextRetryAt,
            String errorMessage, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote_file set status='failed',retry_count=:retry,next_retry_at=:next,lease_until=null,
                    error_message=:error,update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and status='running' and row_version=:version
                """, taskUpdate(id, expectedRowVersion, operatorId, now).addValue("retry", retryCount)
                .addValue("next", nextRetryAt == null ? null : Timestamp.from(nextRetryAt))
                .addValue("error", errorMessage)) == 1;
    }

    @Override
    public boolean requeue(long id, long expectedRowVersion, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote_file set status='queued',next_retry_at=null,lease_until=null,error_message=null,
                    update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and status='failed' and retry_count<2 and row_version=:version
                """, taskUpdate(id, expectedRowVersion, operatorId, now)) == 1;
    }

    @Override
    public boolean markDownloaded(long id, long expectedRowVersion, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote_file set last_download_at=:now,download_count=download_count+1,
                    update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and status='success'
                """, taskUpdate(id, expectedRowVersion, operatorId, now)) == 1;
    }

    @Override
    public boolean extendRetention(long id, long expectedRowVersion, Instant retainUntil,
            long operatorId, Instant now) {
        DeliveryFileTask task = findById(id).orElse(null);
        if (task == null || task.rowVersion() != expectedRowVersion || !"success".equals(task.status())) return false;
        List<DeliveryArtifact> updated = task.artifacts().stream().map(artifact -> new DeliveryArtifact(
                artifact.fileName(), artifact.objectKey(), artifact.contentType(), artifact.sha256(),
                artifact.byteSize(), artifact.pageCount(), artifact.role(), artifact.skuCodes(),
                artifact.sourceMode(), artifact.reviewStatus(), retainUntil)).toList();
        return jdbc.update("""
                update fq_quote_file set files_json=:files,update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and status='success' and row_version=:version
                """, taskUpdate(id, expectedRowVersion, operatorId, now).addValue("files", json.write(updated))) == 1;
    }

    private DeliveryQuoteSnapshot mapSnapshotHeader(ResultSet rs) throws SQLException {
        return new DeliveryQuoteSnapshot(rs.getLong("id"), rs.getString("quote_no"), rs.getInt("version_no"),
                rs.getString("title"), rs.getString("customer_name"), rs.getInt("requested_qty"),
                rs.getString("quote_mode"), rs.getString("warehouse_code"), rs.getString("currency"),
                rs.getString("tax_mode"), rs.getBigDecimal("tax_rate"), rs.getString("discount_type"),
                rs.getBigDecimal("discount_rate"), rs.getBigDecimal("fixed_discount"), rs.getBigDecimal("freight"),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_amount"), rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"), rs.getInt("valid_days"), instant(rs, "valid_until"),
                rs.getString("public_note"), rs.getString("content_hash"), instant(rs, "confirmed_at"),
                json.readTree(rs.getString("requirement_json")), json.readTree(rs.getString("presentation_json")),
                List.of());
    }

    private DeliveryQuoteSnapshot.Combo mapCombo(ResultSet rs) throws SQLException {
        return new DeliveryQuoteSnapshot.Combo(rs.getLong("id"), rs.getString("combo_no"), rs.getString("name"),
                rs.getInt("category_count"), rs.getInt("set_qty"), rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("discount_amount"), rs.getBigDecimal("freight"), rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"), selectedImage(rs), List.of());
    }

    private DeliveryQuoteSnapshot.ImageRef selectedImage(ResultSet rs) throws SQLException {
        int number = rs.getInt("selected_image_no");
        if (rs.wasNull()) return null;
        JsonNode values = json.readTree(rs.getString("results_json"));
        JsonNode result = values != null && values.isArray() && number > 0 && number <= values.size()
                ? values.get(number - 1) : null;
        if (result == null) return null;
        return new DeliveryQuoteSnapshot.ImageRef(result.path("object_key").asText(null),
                result.path("sha256").asText(null), rs.getString("source_mode"), "pass");
    }

    private List<DeliveryQuoteSnapshot.Line> findLines(long comboId) {
        return jdbc.query("""
                select line_no,slot_code,product_id,sku_code,style_code,product_name,category_code,color_name,
                       size_code,unit,qty,source_price,quote_price,amount,stock_qty,stock_as_of,image_key,image_hash,remark
                  from fq_quote_detail where combo_id=:combo order by line_no,id
                """, Map.of("combo", comboId), (rs, row) -> new DeliveryQuoteSnapshot.Line(
                rs.getInt("line_no"), rs.getString("slot_code"), rs.getLong("product_id"), rs.getString("sku_code"),
                rs.getString("style_code"), rs.getString("product_name"), rs.getString("category_code"),
                rs.getString("color_name"), rs.getString("size_code"), rs.getString("unit"), rs.getInt("qty"),
                rs.getBigDecimal("source_price"), rs.getBigDecimal("quote_price"), rs.getBigDecimal("amount"),
                nullableInt(rs, "stock_qty"), instant(rs, "stock_as_of"), rs.getString("image_key"),
                rs.getString("image_hash"), rs.getString("remark")));
    }

    private DeliveryFileTask mapTask(ResultSet rs, int row) throws SQLException {
        String raw = rs.getString("files_json");
        List<DeliveryArtifact> artifacts = raw == null ? List.of()
                : Arrays.asList(mapper.convertValue(json.readTree(raw), DeliveryArtifact[].class));
        return new DeliveryFileTask(rs.getLong("id"), rs.getLong("quote_id"), rs.getString("quote_no"),
                rs.getInt("version_no"), rs.getString("quote_title"), rs.getString("file_type"),
                rs.getString("purpose"), rs.getString("quote_hash"), rs.getString("renderer_version"),
                rs.getString("request_key"), rs.getString("status"), List.copyOf(artifacts),
                rs.getInt("retry_count"), instant(rs, "next_retry_at"), instant(rs, "lease_until"),
                rs.getString("error_message"), instant(rs, "last_download_at"), rs.getInt("download_count"),
                rs.getLong("create_by"), instant(rs, "create_time"), rs.getLong("row_version"));
    }

    private MapSqlParameterSource taskUpdate(long id, long version, long operator, Instant now) {
        return new MapSqlParameterSource().addValue("id", id).addValue("version", version)
                .addValue("operator", operator).addValue("now", Timestamp.from(now));
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
