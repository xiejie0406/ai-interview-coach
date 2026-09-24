package com.ruoyi.fashion.infrastructure.persistence.image;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.fashion.application.image.QuoteImageTask;
import com.ruoyi.fashion.application.image.port.FashionQuoteImageRepository;
import com.ruoyi.fashion.domain.product.FashionProductImage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFashionQuoteImageRepository implements FashionQuoteImageRepository {
    private static final String SELECT = """
            select i.*,c.quote_id,(c.selected_image_id=i.id) adopted,c.selected_image_no adopted_result_no
              from fq_quote_image i join fq_quote_combo c on c.id=i.combo_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcFashionQuoteImageRepository(
            @Qualifier("fashionJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<QuoteImageTask> findByQuoteId(long quoteId) {
        return jdbc.query(SELECT + " where c.quote_id=:quote order by i.create_time desc,i.id desc",
                Map.of("quote", quoteId), this::map);
    }

    @Override
    public Optional<QuoteImageTask> findById(long imageId) {
        return jdbc.query(SELECT + " where i.id=:id", Map.of("id", imageId), this::map).stream().findFirst();
    }

    @Override
    public Optional<QuoteImageTask> findByRequestKey(String requestKey) {
        return jdbc.query(SELECT + " where i.request_key=:key", Map.of("key", requestKey), this::map)
                .stream().findFirst();
    }

    @Override
    public JsonNode currentInputs(long comboId) {
        record Row(String slot, long productId, long visualVersion, String imageKey,
                   String imagesJson, String category, String style, String color) {}
        List<Row> rows = jdbc.query("""
                select distinct d.slot_code,d.product_id,p.visual_version,p.main_image_key,p.images_json,
                       d.category_code,d.style_code,d.color_code
                  from fq_quote_detail d join fq_product p on p.id=d.product_id
                 where d.combo_id=:combo order by d.slot_code,d.product_id
                """, Map.of("combo", comboId), (rs, n) -> new Row(rs.getString("slot_code"),
                rs.getLong("product_id"), rs.getLong("visual_version"), rs.getString("main_image_key"),
                rs.getString("images_json"), rs.getString("category_code"), rs.getString("style_code"),
                rs.getString("color_code")));
        Map<String, List<Row>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(row.slot(), ignored -> new ArrayList<>()).add(row));
        ArrayNode result = objectMapper.createArrayNode();
        grouped.forEach((slot, products) -> {
            ObjectNode group = result.addObject();
            group.put("slot_code", slot);
            ArrayNode productValues = group.putArray("products");
            for (Row row : products) {
                ObjectNode product = productValues.addObject();
                product.put("product_id", Long.toString(row.productId()));
                product.put("visual_version", row.visualVersion());
                product.put("category_code", row.category());
                product.put("style_code", row.style());
                product.put("color_code", row.color());
            }
            Row first = products.get(0);
            FashionProductImage image = images(first.imagesJson()).stream()
                    .filter(item -> first.imageKey() != null && first.imageKey().equals(item.objectKey()))
                    .findFirst().orElse(null);
            putNullable(group, "image_key", first.imageKey());
            putNullable(group, "image_hash", image == null ? null : image.sha256());
            ObjectNode permission = group.putObject("permission_snapshot");
            permission.put("allow_ai", image != null && image.allowAi() && "active".equals(image.status()));
            permission.put("allow_proposal", image != null && image.allowProposal() && "active".equals(image.status()));
            permission.put("allow_ecommerce", image != null && image.allowEcommerce() && "active".equals(image.status()));
        });
        return result;
    }

    @Override
    public void insert(QuoteImageTask task, long operatorId, Instant now) {
        int changed = jdbc.update("""
                insert into fq_quote_image
                  (id,combo_id,ai_run_id,source_image_id,image_type,source_mode,input_hash,inputs_json,
                   parameters_json,requested_count,results_json,provider_code,provider_task_id,request_key,
                   status,stale,retry_count,next_retry_at,lease_until,estimated_cost,actual_cost,cost_currency,
                   billing_status,billing_events_json,error_message,finished_at,create_by,create_time,
                   update_by,update_time,row_version)
                values
                  (:id,:combo,:run,:source,:type,:mode,:hash,:inputs,:parameters,:count,:results,:provider,
                   :providerTask,:requestKey,:status,:stale,:retryCount,:nextRetry,:lease,:estimated,:actual,
                   :currency,:billing,:billingEvents,:error,:finished,:operator,:now,:operator,:now,:version)
                """, values(task, operatorId, now));
        if (changed != 1) throw new IllegalStateException("图片任务未写入一行");
    }

    @Override
    public Optional<QuoteImageTask> claimNext(String workerId, Instant now, Instant leaseUntil) {
        List<Long> ids = jdbc.queryForList("""
                select id from fq_quote_image
                 where ((status in ('queued','unknown','cancel_requested') and (next_retry_at is null or next_retry_at<=:now))
                    or (status='running' and lease_until<:now))
                 order by create_time,id limit 1 for update skip locked
                """, Map.of("now", Timestamp.from(now)), Long.class);
        if (ids.isEmpty()) return Optional.empty();
        long id = ids.get(0);
        QuoteImageTask claimed = findById(id).orElseThrow();
        jdbc.update("""
                update fq_quote_image set status=case when status='cancel_requested' then status else 'running' end,
                       lease_until=:lease,update_time=:now,row_version=row_version+1 where id=:id
                """, new MapSqlParameterSource().addValue("id", id).addValue("lease", Timestamp.from(leaseUntil))
                .addValue("now", Timestamp.from(now)));
        return Optional.of(new QuoteImageTask(claimed.id(), claimed.quoteId(), claimed.comboId(), claimed.aiRunId(),
                claimed.sourceImageId(), claimed.imageType(), claimed.sourceMode(), claimed.inputHash(),
                claimed.inputs(), claimed.parameters(), claimed.requestedCount(), claimed.results(),
                claimed.providerCode(), claimed.providerTaskId(), claimed.requestKey(), claimed.status(),
                claimed.stale(), claimed.retryCount(), claimed.nextRetryAt(), leaseUntil, claimed.estimatedCost(),
                claimed.actualCost(), claimed.costCurrency(), claimed.billingStatus(), claimed.billingEvents(),
                claimed.errorMessage(), claimed.finishedAt(), claimed.createTime(), claimed.adopted(),
                claimed.adoptedResultNo(), claimed.rowVersion() + 1));
    }

    @Override
    public boolean updateExecution(long imageId, long fencingVersion, String status, JsonNode results,
            String providerCode, String providerTaskId, int retryCount, Instant nextRetryAt,
            BigDecimal actualCost, String billingStatus, JsonNode billingEvents,
            String errorMessage, Instant finishedAt, Instant now) {
        return jdbc.update("""
                update fq_quote_image set status=:status,results_json=:results,provider_code=:provider,
                       provider_task_id=:providerTask,retry_count=:retry,next_retry_at=:nextRetry,
                       lease_until=null,actual_cost=:actual,billing_status=:billing,
                       billing_events_json=:billingEvents,error_message=:error,finished_at=:finished,
                       update_time=:now,row_version=row_version+1
                 where id=:id and row_version=:version
                """, new MapSqlParameterSource().addValue("id", imageId).addValue("version", fencingVersion)
                .addValue("status", status).addValue("results", json(results)).addValue("provider", providerCode)
                .addValue("providerTask", providerTaskId).addValue("retry", retryCount)
                .addValue("nextRetry", timestamp(nextRetryAt)).addValue("actual", actualCost)
                .addValue("billing", billingStatus).addValue("billingEvents", json(billingEvents))
                .addValue("error", errorMessage).addValue("finished", timestamp(finishedAt))
                .addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public boolean requestCancel(long imageId, long expectedRowVersion, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote_image
                   set status=case when status in ('queued','unknown') then 'cancelled' else 'cancel_requested' end,
                       finished_at=case when status in ('queued','unknown') then :now else finished_at end,
                       update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and row_version=:version and status in ('queued','running','unknown')
                """, new MapSqlParameterSource().addValue("id", imageId).addValue("version", expectedRowVersion)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public boolean updateReview(long imageId, JsonNode results, long expectedRowVersion, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote_image set results_json=:results,update_by=:operator,update_time=:now,
                       row_version=row_version+1 where id=:id and row_version=:version
                """, new MapSqlParameterSource().addValue("id", imageId).addValue("version", expectedRowVersion)
                .addValue("results", json(results)).addValue("operator", operatorId)
                .addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public boolean markStale(long imageId, long expectedRowVersion, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote_image set stale=1,update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and row_version=:version and stale=0
                """, new MapSqlParameterSource().addValue("id", imageId).addValue("version", expectedRowVersion)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public boolean adopt(long comboId, long imageId, int resultNo, String comboVisualHash,
            long expectedImageVersion, long operatorId, Instant now) {
        int changed = jdbc.update("""
                update fq_quote_combo c join fq_quote q on q.id=c.quote_id
                   set c.selected_image_id=:image,c.selected_image_no=:resultNo,c.update_by=:operator,
                       c.update_time=:now,c.row_version=c.row_version+1
                 where c.id=:combo and c.visual_hash=:visualHash and c.selected=1 and q.status='draft'
                   and exists(select 1 from fq_quote_image i where i.id=:image and i.combo_id=c.id
                              and i.row_version=:imageVersion and i.stale=0)
                """, new MapSqlParameterSource().addValue("image", imageId).addValue("resultNo", resultNo)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now)).addValue("combo", comboId)
                .addValue("visualHash", comboVisualHash).addValue("imageVersion", expectedImageVersion));
        return changed == 1;
    }

    @Override
    public BigDecimal settledCostSince(Instant since) {
        BigDecimal result = jdbc.queryForObject("""
                select coalesce(sum(actual_cost),0) from fq_quote_image
                 where billing_status='settled' and cost_currency='CNY' and create_time>=:since
                """, Map.of("since", Timestamp.from(since)), BigDecimal.class);
        return result == null ? BigDecimal.ZERO : result;
    }

    @Override
    public BigDecimal committedCostSince(Instant since) {
        BigDecimal result = jdbc.queryForObject("""
                select coalesce(sum(case when billing_status='settled' then actual_cost else estimated_cost end),0)
                  from fq_quote_image
                 where billing_status in ('reserved','unknown','settled') and cost_currency='CNY'
                   and create_time>=:since
                """, Map.of("since", Timestamp.from(since)), BigDecimal.class);
        return result == null ? BigDecimal.ZERO : result;
    }

    @Override
    public void lockMonthlyBudget() {
        jdbc.queryForList("select config_id from sys_config where config_key='fashion.ai.monthlyBudgetCny' for update",
                Map.of(), Long.class);
    }

    private QuoteImageTask map(ResultSet rs, int rowNum) throws SQLException {
        return new QuoteImageTask(rs.getLong("id"), rs.getLong("quote_id"), rs.getLong("combo_id"),
                nullableLong(rs, "ai_run_id"), nullableLong(rs, "source_image_id"), rs.getString("image_type"),
                rs.getString("source_mode"), rs.getString("input_hash"), readTree(rs.getString("inputs_json")),
                readTree(rs.getString("parameters_json")), rs.getInt("requested_count"),
                readTree(rs.getString("results_json")), rs.getString("provider_code"),
                rs.getString("provider_task_id"), rs.getString("request_key"), rs.getString("status"),
                rs.getInt("stale") == 1, rs.getInt("retry_count"), instant(rs, "next_retry_at"),
                instant(rs, "lease_until"), rs.getBigDecimal("estimated_cost"), rs.getBigDecimal("actual_cost"),
                rs.getString("cost_currency"), rs.getString("billing_status"),
                readTree(rs.getString("billing_events_json")), rs.getString("error_message"),
                instant(rs, "finished_at"), instant(rs, "create_time"), rs.getInt("adopted") == 1,
                nullableInt(rs, "adopted_result_no"), rs.getLong("row_version"));
    }

    private MapSqlParameterSource values(QuoteImageTask task, long operatorId, Instant now) {
        return new MapSqlParameterSource().addValue("id", task.id()).addValue("combo", task.comboId())
                .addValue("run", task.aiRunId()).addValue("source", task.sourceImageId())
                .addValue("type", task.imageType()).addValue("mode", task.sourceMode())
                .addValue("hash", task.inputHash()).addValue("inputs", json(task.inputs()))
                .addValue("parameters", json(task.parameters())).addValue("count", task.requestedCount())
                .addValue("results", json(task.results())).addValue("provider", task.providerCode())
                .addValue("providerTask", task.providerTaskId()).addValue("requestKey", task.requestKey())
                .addValue("status", task.status()).addValue("stale", task.stale() ? 1 : 0)
                .addValue("retryCount", task.retryCount()).addValue("nextRetry", timestamp(task.nextRetryAt()))
                .addValue("lease", timestamp(task.leaseUntil())).addValue("estimated", task.estimatedCost())
                .addValue("actual", task.actualCost()).addValue("currency", task.costCurrency())
                .addValue("billing", task.billingStatus()).addValue("billingEvents", json(task.billingEvents()))
                .addValue("error", task.errorMessage()).addValue("finished", timestamp(task.finishedAt()))
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now))
                .addValue("version", task.rowVersion());
    }

    private List<FashionProductImage> images(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<FashionProductImage>>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("商品图片 JSON 无效", exception);
        }
    }

    private JsonNode readTree(String json) {
        try { return objectMapper.readTree(json); }
        catch (Exception exception) { throw new IllegalStateException("图片任务 JSON 无效", exception); }
    }

    private String json(JsonNode value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("图片任务 JSON 无法编码", exception); }
    }

    private static void putNullable(ObjectNode node, String name, String value) {
        if (value == null) node.putNull(name); else node.put(name, value);
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name); return value == null ? null : value.toInstant();
    }
    private static Long nullableLong(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name); return rs.wasNull() ? null : value;
    }
    private static Integer nullableInt(ResultSet rs, String name) throws SQLException {
        int value = rs.getInt(name); return rs.wasNull() ? null : value;
    }
}
