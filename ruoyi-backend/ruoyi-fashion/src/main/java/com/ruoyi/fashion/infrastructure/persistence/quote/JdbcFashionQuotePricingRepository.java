package com.ruoyi.fashion.infrastructure.persistence.quote;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

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
import java.util.function.LongSupplier;

import tools.jackson.databind.JsonNode;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingState;
import com.ruoyi.fashion.application.quote.pricing.QuotePricingWrite;
import com.ruoyi.fashion.application.quote.pricing.port.FashionQuotePricingRepository;
import com.ruoyi.fashion.infrastructure.persistence.foundation.FashionJsonCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@FashionModuleEnabled
@Repository
public class JdbcFashionQuotePricingRepository implements FashionQuotePricingRepository {
    private static final String QUOTE_COLUMNS = "id,quote_no,version_no,customer_id,customer_name,requested_qty,quote_mode,"
            + "warehouse_code,currency,tax_mode,tax_rate,fee_taxable,discount_type,discount_rate,fixed_discount,"
            + "freight,subtotal,discount_amount,tax_amount,total_amount,approval_json,valid_days,valid_until,"
            + "public_note,content_hash,status,row_version";
    private final NamedParameterJdbcTemplate jdbc;
    private final FashionJsonCodec json;

    public JdbcFashionQuotePricingRepository(
            @Qualifier("fashionJdbcTemplate") NamedParameterJdbcTemplate jdbc, FashionJsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<QuotePricingState> find(long quoteId, boolean forUpdate) {
        List<QuotePricingState> headers = jdbc.query("select " + QUOTE_COLUMNS
                + " from fq_quote where id=:id" + (forUpdate ? " for update" : ""), Map.of("id", quoteId),
                (rs, rowNum) -> mapQuote(rs, List.of()));
        if (headers.isEmpty()) return Optional.empty();
        List<QuotePricingState.Combo> combos = jdbc.query("""
                select c.id,c.combo_no,c.name,c.category_count,c.set_qty,c.selected,c.allocation_confirmed,
                       c.selected_image_id,c.selected_image_no,c.visual_hash,c.subtotal,c.discount_amount,c.freight,
                       c.tax_amount,c.total_amount,c.row_version,i.status image_status,i.stale image_stale,
                       i.input_hash image_input_hash,i.inputs_json image_inputs,i.results_json image_results
                  from fq_quote_combo c left join fq_quote_image i
                    on i.id=c.selected_image_id and i.combo_id=c.id
                 where c.quote_id=:quote order by c.sort_no,c.id
                """, Map.of("quote", quoteId), (rs, rowNum) -> mapCombo(rs, List.of()));
        List<QuotePricingState.Combo> populated = combos.stream()
                .map(combo -> mapComboWithLines(combo, findLines(combo.id(), headers.get(0).warehouseCode())))
                .toList();
        QuotePricingState h = headers.get(0);
        return Optional.of(new QuotePricingState(h.quoteId(), h.quoteNo(), h.versionNo(), h.customerId(),
                h.customerName(), h.requestedQty(), h.mode(), h.warehouseCode(), h.currency(), h.taxMode(), h.taxRate(),
                h.feeTaxable(), h.discountType(), h.discountRate(), h.fixedDiscount(), h.freight(), h.subtotal(),
                h.discountAmount(), h.taxAmount(), h.totalAmount(), h.approval(), h.validDays(), h.validUntil(),
                h.publicNote(), h.contentHash(), h.status(), h.rowVersion(), populated));
    }

    @Override
    public boolean saveDraft(QuotePricingWrite write) {
        if (!updateDraftHeader(write)) return false;
        persistStructure(write);
        return true;
    }

    @Override
    public boolean updateApproval(long quoteId, JsonNode approval, String inputHash, long expectedRowVersion,
            long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote set approval_json=:approval,content_hash=:hash,update_by=:operator,update_time=:now,
                    row_version=row_version+1 where id=:id and status='draft' and row_version=:version
                """, new MapSqlParameterSource().addValue("approval", json.write(approval)).addValue("hash", inputHash)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now)).addValue("id", quoteId)
                .addValue("version", expectedRowVersion)) == 1;
    }

    @Override
    public boolean confirm(QuotePricingWrite write, long confirmedBy, Instant validUntil) {
        persistStructure(write);
        return jdbc.update("""
                update fq_quote set quote_mode=:mode,tax_mode=:taxMode,tax_rate=:taxRate,fee_taxable=:feeTaxable,
                    discount_type=:discountType,discount_rate=:discountRate,fixed_discount=:fixedDiscount,
                    freight=:freight,subtotal=:subtotal,discount_amount=:discount,tax_amount=:tax,total_amount=:total,
                    approval_json=:approval,valid_days=:validDays,valid_until=:validUntil,public_note=:publicNote,
                    content_hash=:hash,confirmed_by=:confirmedBy,confirmed_at=:now,status='confirmed',
                    update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and status='draft' and row_version=:version
                """, headerParameters(write).addValue("validUntil", Timestamp.from(validUntil))
                .addValue("confirmedBy", confirmedBy)) == 1;
    }

    @Override
    public void copyStructure(long sourceQuoteId, long targetQuoteId, LongSupplier ids, long operatorId, Instant now) {
        jdbc.update("""
                update fq_quote target join fq_quote source on source.id=:source
                   set target.quote_mode=source.quote_mode,target.tax_mode=source.tax_mode,target.tax_rate=source.tax_rate,
                       target.fee_taxable=source.fee_taxable,target.discount_type=source.discount_type,
                       target.discount_rate=source.discount_rate,target.fixed_discount=source.fixed_discount,
                       target.freight=source.freight,target.valid_days=source.valid_days,target.public_note=source.public_note,
                       target.approval_json=null,target.subtotal=null,target.discount_amount=null,target.tax_amount=null,
                       target.total_amount=null,target.content_hash=null
                 where target.id=:target and target.status='draft'
                """, Map.of("source", sourceQuoteId, "target", targetQuoteId));
        List<Map<String, Object>> sourceCombos = jdbc.queryForList(
                "select * from fq_quote_combo where quote_id=:quote order by sort_no,id", Map.of("quote", sourceQuoteId));
        for (Map<String, Object> source : sourceCombos) {
            long sourceComboId = ((Number) source.get("id")).longValue();
            long targetComboId = ids.getAsLong();
            jdbc.update("""
                    insert into fq_quote_combo (id,quote_id,combo_no,name,category_count,set_qty,selected,sort_no,reason,
                        lock_json,visual_hash,allocation_confirmed,allocation_confirmed_by,allocation_confirmed_at,
                        selected_image_id,selected_image_no,subtotal,discount_amount,freight,tax_amount,total_amount,
                        create_by,create_time,update_by,update_time,row_version)
                    select :id,:target,combo_no,name,category_count,set_qty,selected,sort_no,reason,lock_json,visual_hash,
                        allocation_confirmed,null,null,null,null,null,null,null,null,null,
                        :operator,:now,:operator,:now,1 from fq_quote_combo where id=:source
                    """, new MapSqlParameterSource().addValue("id", targetComboId).addValue("target", targetQuoteId)
                    .addValue("operator", operatorId).addValue("now", Timestamp.from(now))
                    .addValue("source", sourceComboId));
            List<Map<String, Object>> details = jdbc.queryForList(
                    "select id from fq_quote_detail where combo_id=:combo order by line_no,id",
                    Map.of("combo", sourceComboId));
            for (Map<String, Object> detail : details) {
                jdbc.update("""
                        insert into fq_quote_detail (id,combo_id,ai_run_id,line_no,slot_code,product_id,source_code,
                            sku_code,style_code,product_name,category_code,color_code,color_name,size_code,size_system,
                            unit,qty,source_price,quote_price,amount,price_batch_id,stock_batch_id,stock_qty,stock_as_of,
                            image_key,image_hash,image_version,image_source_json,remark,create_by,create_time,update_by,
                            update_time,row_version)
                        select :id,:combo,ai_run_id,line_no,slot_code,product_id,source_code,sku_code,style_code,
                            product_name,category_code,color_code,color_name,size_code,size_system,unit,qty,source_price,
                            quote_price,null,price_batch_id,stock_batch_id,stock_qty,stock_as_of,image_key,image_hash,
                            image_version,image_source_json,remark,:operator,:now,:operator,:now,1
                          from fq_quote_detail where id=:source
                        """, new MapSqlParameterSource().addValue("id", ids.getAsLong()).addValue("combo", targetComboId)
                        .addValue("operator", operatorId).addValue("now", Timestamp.from(now))
                        .addValue("source", ((Number) detail.get("id")).longValue()));
            }
        }
    }

    private boolean updateDraftHeader(QuotePricingWrite write) {
        return jdbc.update("""
                update fq_quote set quote_mode=:mode,tax_mode=:taxMode,tax_rate=:taxRate,fee_taxable=:feeTaxable,
                    discount_type=:discountType,discount_rate=:discountRate,fixed_discount=:fixedDiscount,
                    freight=:freight,subtotal=:subtotal,discount_amount=:discount,tax_amount=:tax,total_amount=:total,
                    approval_json=null,valid_days=:validDays,valid_until=null,public_note=:publicNote,
                    content_hash=:hash,confirmed_by=null,confirmed_at=null,update_by=:operator,update_time=:now,
                    row_version=row_version+1 where id=:id and status='draft' and row_version=:version
                """, headerParameters(write).addValue("approval", null)) == 1;
    }

    private MapSqlParameterSource headerParameters(QuotePricingWrite w) {
        return new MapSqlParameterSource().addValue("id", w.quoteId()).addValue("version", w.expectedRowVersion())
                .addValue("mode", w.mode()).addValue("taxMode", w.taxMode()).addValue("taxRate", w.taxRate())
                .addValue("feeTaxable", w.feeTaxable() ? 1 : 0).addValue("discountType", w.discountType())
                .addValue("discountRate", w.discountRate()).addValue("fixedDiscount", w.fixedDiscount())
                .addValue("freight", w.freight()).addValue("subtotal", w.subtotal())
                .addValue("discount", w.discountAmount()).addValue("tax", w.taxAmount()).addValue("total", w.totalAmount())
                .addValue("approval", w.approval() == null ? null : json.write(w.approval()))
                .addValue("validDays", w.validDays()).addValue("publicNote", w.publicNote())
                .addValue("hash", w.contentHash()).addValue("operator", w.operatorId())
                .addValue("now", Timestamp.from(w.now()));
    }

    private void persistStructure(QuotePricingWrite write) {
        for (QuotePricingWrite.Combo combo : write.combos()) {
            int comboChanged = jdbc.update("""
                    update fq_quote_combo set selected=:selected,allocation_confirmed=:allocation,
                        allocation_confirmed_by=case when :allocation=1 then :operator else null end,
                        allocation_confirmed_at=case when :allocation=1 then :now else null end,
                        subtotal=:subtotal,discount_amount=:discount,freight=:freight,tax_amount=:tax,
                        total_amount=:total,update_by=:operator,update_time=:now,row_version=row_version+1
                     where id=:id and quote_id=:quote
                    """, new MapSqlParameterSource().addValue("selected", combo.selected() ? 1 : 0)
                    .addValue("allocation", combo.allocationConfirmed() ? 1 : 0)
                    .addValue("operator", write.operatorId()).addValue("now", Timestamp.from(write.now()))
                    .addValue("subtotal", combo.subtotal()).addValue("discount", combo.discountAmount())
                    .addValue("freight", combo.freight()).addValue("tax", combo.taxAmount())
                    .addValue("total", combo.totalAmount()).addValue("id", combo.id())
                    .addValue("quote", write.quoteId()));
            if (comboChanged != 1) throw new IllegalStateException("报价组合已不存在");
            for (QuotePricingWrite.Line line : combo.lines()) {
                int lineChanged = jdbc.update("""
                        update fq_quote_detail set qty=:qty,source_price=:sourcePrice,quote_price=:quotePrice,
                            amount=:amount,price_batch_id=:priceBatch,stock_batch_id=:stockBatch,stock_qty=:stockQty,
                            stock_as_of=:stockAsOf,image_key=:imageKey,image_hash=:imageHash,image_version=:imageVersion,
                            update_by=:operator,update_time=:now,row_version=row_version+1
                         where id=:id and combo_id=:combo
                        """, new MapSqlParameterSource().addValue("qty", line.qty())
                        .addValue("sourcePrice", line.sourcePrice()).addValue("quotePrice", line.quotePrice())
                        .addValue("amount", line.amount()).addValue("priceBatch", line.priceBatchId())
                        .addValue("stockBatch", line.stockBatchId()).addValue("stockQty", line.stockQty())
                        .addValue("stockAsOf", line.stockAsOf() == null ? null : Timestamp.from(line.stockAsOf()))
                        .addValue("imageKey", line.imageKey()).addValue("imageHash", line.imageHash())
                        .addValue("imageVersion", line.imageVersion()).addValue("operator", write.operatorId())
                        .addValue("now", Timestamp.from(write.now())).addValue("id", line.id())
                        .addValue("combo", combo.id()));
                if (lineChanged != 1) throw new IllegalStateException("报价明细已不存在");
            }
        }
    }

    private List<QuotePricingState.Line> findLines(long comboId, String warehouse) {
        return jdbc.query("""
                select d.id,d.slot_code,d.product_id,d.sku_code,d.product_name,d.category_code,d.color_name,
                       d.size_code,d.unit,d.qty,d.source_price,d.quote_price,d.amount,d.price_batch_id frozen_price_batch,
                       d.image_key,d.image_hash,
                       d.image_version,d.stock_qty frozen_stock_qty,d.stock_as_of frozen_stock_as_of,
                       d.stock_batch_id frozen_stock_batch,d.row_version,
                       p.sale_price current_price,p.currency current_currency,p.tax_mode current_tax_mode,
                       p.price_as_of current_price_as_of,p.last_price_import_batch_id current_price_batch,
                       p.row_version current_product_version,p.status current_status,p.main_image_key current_image_key,
                       (select image.sha256 from json_table(p.images_json,'$[*]' columns (
                           object_key varchar(512) path '$.objectKey', sha256 char(64) path '$.sha256',
                           allow_proposal boolean path '$.allowProposal', image_status varchar(16) path '$.status'
                       )) image where image.object_key collate utf8mb4_unicode_ci=p.main_image_key limit 1) current_image_hash,
                       coalesce((select image.allow_proposal and image.image_status='active'
                           from json_table(p.images_json,'$[*]' columns (
                               object_key varchar(512) path '$.objectKey',
                               allow_proposal boolean path '$.allowProposal', image_status varchar(16) path '$.status'
                           )) image where image.object_key collate utf8mb4_unicode_ci=p.main_image_key limit 1),false) current_image_allowed,
                       p.visual_version current_visual_version,
                       s.available_qty current_stock_qty,s.as_of current_stock_as_of,
                       s.last_import_batch_id current_stock_batch,s.row_version current_stock_version
                  from fq_quote_detail d join fq_product p on p.id=d.product_id
                  left join fq_stock s on s.product_id=p.id and s.warehouse_code=:warehouse
                 where d.combo_id=:combo order by d.line_no,d.id
                """, new MapSqlParameterSource().addValue("combo", comboId).addValue("warehouse", warehouse),
                this::mapLine);
    }

    private QuotePricingState mapQuote(ResultSet rs, List<QuotePricingState.Combo> combos) throws SQLException {
        String approval = rs.getString("approval_json");
        return new QuotePricingState(rs.getLong("id"), rs.getString("quote_no"), rs.getInt("version_no"),
                rs.getLong("customer_id"), rs.getString("customer_name"), rs.getInt("requested_qty"),
                rs.getString("quote_mode"),
                rs.getString("warehouse_code"), rs.getString("currency"), rs.getString("tax_mode"),
                rs.getBigDecimal("tax_rate"), rs.getInt("fee_taxable") == 1, rs.getString("discount_type"),
                rs.getBigDecimal("discount_rate"), rs.getBigDecimal("fixed_discount"), rs.getBigDecimal("freight"),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_amount"), rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"), approval == null ? null : json.readTree(approval),
                rs.getInt("valid_days"), instant(rs, "valid_until"), rs.getString("public_note"),
                rs.getString("content_hash"), rs.getString("status"), rs.getLong("row_version"), combos);
    }

    private QuotePricingState.Combo mapCombo(ResultSet rs, List<QuotePricingState.Line> lines) throws SQLException {
        return new QuotePricingState.Combo(rs.getLong("id"), rs.getString("combo_no"), rs.getString("name"),
                rs.getInt("category_count"), rs.getInt("set_qty"), rs.getInt("selected") == 1,
                rs.getInt("allocation_confirmed") == 1,
                nullableLong(rs, "selected_image_id"), nullableInt(rs, "selected_image_no"),
                rs.getString("visual_hash"), selectedImageHash(rs), selectedImageValid(rs),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("freight"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("total_amount"),
                rs.getLong("row_version"), lines);
    }

    private QuotePricingState.Combo mapComboWithLines(
            QuotePricingState.Combo c, List<QuotePricingState.Line> lines) {
        return new QuotePricingState.Combo(c.id(), c.comboNo(), c.name(), c.categoryCount(), c.setQty(), c.selected(),
                c.allocationConfirmed(), c.selectedImageId(), c.selectedImageNo(), c.visualHash(),
                c.selectedImageHash(), c.selectedImageValid(), c.subtotal(),
                c.discountAmount(), c.freight(), c.taxAmount(), c.totalAmount(), c.rowVersion(), lines);
    }

    private String selectedImageHash(ResultSet rs) throws SQLException {
        Integer number = nullableInt(rs, "selected_image_no");
        String raw = rs.getString("image_results");
        if (number == null || raw == null) return null;
        JsonNode results = json.readTree(raw);
        JsonNode result = results.isArray() && number <= results.size() ? results.get(number - 1) : null;
        return result == null ? null : result.path("sha256").asText(null);
    }

    private boolean selectedImageValid(ResultSet rs) throws SQLException {
        if (nullableLong(rs, "selected_image_id") == null) return true;
        Integer number = nullableInt(rs, "selected_image_no");
        String rawResults = rs.getString("image_results");
        String rawInputs = rs.getString("image_inputs");
        String imageStatus = rs.getString("image_status");
        if (number == null || rawResults == null || rawInputs == null
                || !("success".equals(imageStatus) || "partial".equals(imageStatus))
                || rs.getInt("image_stale") != 0) return false;
        JsonNode results = json.readTree(rawResults);
        JsonNode result = results.isArray() && number <= results.size() ? results.get(number - 1) : null;
        JsonNode reviews = result == null ? null : result.path("reviews");
        JsonNode latest = reviews != null && reviews.isArray() && !reviews.isEmpty()
                ? reviews.get(reviews.size() - 1) : null;
        return result != null && "success".equals(result.path("status").asText())
                && result.path("allow_proposal").asBoolean(false)
                && result.path("sha256").asText("").matches("[a-fA-F0-9]{64}")
                && latest != null && "pass".equals(latest.path("decision").asText())
                && rs.getString("image_input_hash").equals(latest.path("input_hash").asText())
                && rs.getString("visual_hash").equals(json.readTree(rawInputs).path("combo_visual_hash").asText());
    }

    private QuotePricingState.Line mapLine(ResultSet rs, int rowNum) throws SQLException {
        return new QuotePricingState.Line(rs.getLong("id"), rs.getString("slot_code"), rs.getLong("product_id"),
                rs.getString("sku_code"), rs.getString("product_name"), rs.getString("category_code"),
                rs.getString("color_name"), rs.getString("size_code"), rs.getString("unit"), rs.getInt("qty"),
                rs.getBigDecimal("source_price"), rs.getBigDecimal("quote_price"), rs.getBigDecimal("amount"),
                nullableLong(rs, "frozen_price_batch"),
                rs.getBigDecimal("current_price"), rs.getString("current_currency"), rs.getString("current_tax_mode"),
                instant(rs, "current_price_as_of"), nullableLong(rs, "current_price_batch"),
                rs.getLong("current_product_version"), rs.getString("current_status"),
                nullableInt(rs, "frozen_stock_qty"), instant(rs, "frozen_stock_as_of"),
                nullableLong(rs, "frozen_stock_batch"),
                nullableInt(rs, "current_stock_qty"), instant(rs, "current_stock_as_of"),
                nullableLong(rs, "current_stock_batch"), nullableLongValue(rs, "current_stock_version"),
                rs.getString("image_key"), rs.getString("image_hash"), nullableLong(rs, "image_version"),
                rs.getString("current_image_key"), rs.getString("current_image_hash"),
                rs.getBoolean("current_image_allowed"), rs.getLong("current_visual_version"),
                rs.getLong("row_version"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }
    private static long nullableLongValue(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? 0L : value;
    }
    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column); return rs.wasNull() ? null : value;
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
}
