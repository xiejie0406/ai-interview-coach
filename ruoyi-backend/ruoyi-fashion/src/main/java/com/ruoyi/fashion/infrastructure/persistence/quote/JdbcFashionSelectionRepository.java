package com.ruoyi.fashion.infrastructure.persistence.quote;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.fashion.application.selection.SelectionComboView;
import com.ruoyi.fashion.application.selection.SelectionComboWrite;
import com.ruoyi.fashion.application.selection.SelectionDetailView;
import com.ruoyi.fashion.application.selection.SelectionDetailWrite;
import com.ruoyi.fashion.application.selection.SelectionProductFact;
import com.ruoyi.fashion.application.selection.port.FashionSelectionRepository;
import com.ruoyi.fashion.domain.product.FashionProductImage;
import com.ruoyi.fashion.infrastructure.persistence.foundation.FashionJsonCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@FashionModuleEnabled
@Repository
public class JdbcFashionSelectionRepository implements FashionSelectionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final FashionJsonCodec json;

    public JdbcFashionSelectionRepository(
            @Qualifier("fashionJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            FashionJsonCodec json) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.json = json;
    }

    @Override
    public List<SelectionProductFact> findCandidateFacts(
            Collection<String> categories, String warehouseCode) {
        if (categories == null || categories.isEmpty()) return List.of();
        return jdbc.query("""
                select p.id,p.source_code,p.sku_code,p.style_code,p.name,p.category_code,
                       p.color_code,p.color_name,p.size_code,p.size_system,p.unit,p.sale_price,
                       p.price_as_of,p.last_price_import_batch_id,p.season,p.tags_json,
                       p.main_image_key,p.images_json,p.visual_version,p.row_version product_row_version,
                       s.available_qty,s.as_of stock_as_of,s.last_import_batch_id stock_batch_id,
                       s.row_version stock_row_version
                  from fq_product p
                  join fq_stock s on s.product_id=p.id and s.warehouse_code=:warehouse
                 where p.category_code in (:categories) and p.status='active'
                   and p.attributes_confirmed=1 and p.main_image_key is not null
                   and p.main_image_key<>'' and p.sale_price is not null and p.price_as_of is not null
                   and p.last_price_import_batch_id is not null and s.available_qty>0
                   and s.as_of is not null and s.last_import_batch_id is not null
                 order by p.category_code,p.source_code,p.style_code,p.color_code,p.size_code,p.id
                """, new MapSqlParameterSource().addValue("categories", categories)
                .addValue("warehouse", warehouseCode), this::mapProductFact);
    }

    @Override
    public List<SelectionComboView> findByQuoteId(long quoteId) {
        List<SelectionComboView> combos = jdbc.query("""
                select id,quote_id,combo_no,name,category_count,set_qty,selected,sort_no,reason,
                       lock_json,visual_hash,row_version
                  from fq_quote_combo where quote_id=:quote order by selected desc,sort_no,id
                """, Map.of("quote", quoteId), this::mapCombo);
        return combos.stream().map(combo -> withDetails(combo, findDetails(Long.parseLong(combo.id())))).toList();
    }

    @Override
    public Optional<SelectionComboView> findCombo(long quoteId, long comboId) {
        return jdbc.query("""
                select id,quote_id,combo_no,name,category_count,set_qty,selected,sort_no,reason,
                       lock_json,visual_hash,row_version
                  from fq_quote_combo where quote_id=:quote and id=:id
                """, Map.of("quote", quoteId, "id", comboId), this::mapCombo).stream()
                .findFirst().map(combo -> withDetails(combo, findDetails(comboId)));
    }

    @Override
    public void deselectCurrent(long quoteId, long operatorId, Instant now) {
        jdbc.update("""
                update fq_quote_combo set selected=0,update_by=:operator,update_time=:now,row_version=row_version+1
                 where quote_id=:quote and selected=1
                """, new MapSqlParameterSource().addValue("quote", quoteId)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now)));
    }

    @Override
    public boolean deselectCombo(long comboId, long expectedRowVersion, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote_combo set selected=0,update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and selected=1 and row_version=:version
                """, new MapSqlParameterSource().addValue("id", comboId).addValue("version", expectedRowVersion)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public void insertCombo(SelectionComboWrite combo, List<SelectionDetailWrite> details) {
        int changed = jdbc.update("""
                insert into fq_quote_combo (
                    id,quote_id,combo_no,name,category_count,set_qty,selected,sort_no,reason,lock_json,
                    visual_hash,allocation_confirmed,allocation_confirmed_by,allocation_confirmed_at,
                    selected_image_id,selected_image_no,subtotal,discount_amount,freight,tax_amount,total_amount,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:quote,:no,:name,:categories,:qty,1,:sort,:reason,:locks,:visual,0,null,null,
                    null,null,null,null,null,null,null,:operator,:now,:operator,:now,1)
                """, new MapSqlParameterSource().addValue("id", combo.id()).addValue("quote", combo.quoteId())
                .addValue("no", combo.comboNo()).addValue("name", combo.name())
                .addValue("categories", combo.categoryCount()).addValue("qty", combo.setQty())
                .addValue("sort", combo.sortNo()).addValue("reason", combo.reason())
                .addValue("locks", json.write(objectMapper.valueToTree(combo.lockedSlots())))
                .addValue("visual", combo.visualHash()).addValue("operator", combo.operatorId())
                .addValue("now", Timestamp.from(combo.now())));
        if (changed != 1) throw new IllegalStateException("选品组合未写入一行");
        details.forEach(this::insertDetail);
    }

    @Override
    public boolean updateLocks(
            long comboId, List<String> lockedSlots, long expectedRowVersion, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote_combo set lock_json=:locks,update_by=:operator,update_time=:now,
                    row_version=row_version+1 where id=:id and selected=1 and row_version=:version
                """, new MapSqlParameterSource().addValue("locks", json.write(objectMapper.valueToTree(lockedSlots)))
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now))
                .addValue("id", comboId).addValue("version", expectedRowVersion)) == 1;
    }

    private void insertDetail(SelectionDetailWrite detail) {
        int changed = jdbc.update("""
                insert into fq_quote_detail (
                    id,combo_id,ai_run_id,line_no,slot_code,product_id,source_code,sku_code,style_code,
                    product_name,category_code,color_code,color_name,size_code,size_system,unit,qty,
                    source_price,quote_price,amount,price_batch_id,stock_batch_id,stock_qty,stock_as_of,
                    image_key,image_hash,image_version,image_source_json,remark,
                    create_by,create_time,update_by,update_time,row_version)
                values (:id,:combo,:run,:line,:slot,:product,:source,:sku,:style,:name,:category,:color,
                    :colorName,:size,:sizeSystem,:unit,:qty,:price,null,null,:priceBatch,:stockBatch,:stockQty,
                    :stockAsOf,:imageKey,:imageHash,:imageVersion,null,null,:operator,:now,:operator,:now,1)
                """, new MapSqlParameterSource().addValue("id", detail.id()).addValue("combo", detail.comboId())
                .addValue("run", detail.aiRunId()).addValue("line", detail.lineNo())
                .addValue("slot", detail.slotCode()).addValue("product", detail.productId())
                .addValue("source", detail.sourceCode()).addValue("sku", detail.skuCode())
                .addValue("style", detail.styleCode()).addValue("name", detail.productName())
                .addValue("category", detail.categoryCode()).addValue("color", detail.colorCode())
                .addValue("colorName", detail.colorName()).addValue("size", detail.sizeCode())
                .addValue("sizeSystem", detail.sizeSystem()).addValue("unit", detail.unit())
                .addValue("qty", detail.qty()).addValue("price", detail.sourcePrice())
                .addValue("priceBatch", detail.priceBatchId()).addValue("stockBatch", detail.stockBatchId())
                .addValue("stockQty", detail.stockQty()).addValue("stockAsOf", Timestamp.from(detail.stockAsOf()))
                .addValue("imageKey", detail.imageKey()).addValue("imageHash", detail.imageHash())
                .addValue("imageVersion", detail.imageVersion()).addValue("operator", detail.operatorId())
                .addValue("now", Timestamp.from(detail.now())));
        if (changed != 1) throw new IllegalStateException("选品组合明细未写入一行");
    }

    private List<SelectionDetailView> findDetails(long comboId) {
        return jdbc.query("""
                select id,line_no,slot_code,product_id,source_code,sku_code,style_code,product_name,
                       category_code,color_code,color_name,size_code,qty,source_price,stock_qty,stock_as_of,
                       image_key,image_hash,image_version,row_version
                  from fq_quote_detail where combo_id=:combo order by line_no,id
                """, Map.of("combo", comboId), (rs, rowNum) -> new SelectionDetailView(
                Long.toString(rs.getLong("id")), rs.getInt("line_no"), rs.getString("slot_code"),
                Long.toString(rs.getLong("product_id")), rs.getString("source_code"), rs.getString("sku_code"),
                rs.getString("style_code"), rs.getString("product_name"), rs.getString("category_code"),
                rs.getString("color_code"), rs.getString("color_name"), rs.getString("size_code"),
                rs.getInt("qty"), rs.getBigDecimal("source_price"), nullableInt(rs, "stock_qty"),
                instant(rs, "stock_as_of"), rs.getString("image_key"), rs.getString("image_hash"),
                nullableLong(rs, "image_version"), rs.getLong("row_version")));
    }

    private SelectionProductFact mapProductFact(ResultSet rs, int rowNum) throws SQLException {
        return new SelectionProductFact(rs.getLong("id"), rs.getString("source_code"), rs.getString("sku_code"),
                rs.getString("style_code"), rs.getString("name"), rs.getString("category_code"),
                rs.getString("color_code"), rs.getString("color_name"), rs.getString("size_code"),
                rs.getString("size_system"), rs.getString("unit"), rs.getBigDecimal("sale_price"),
                instant(rs, "price_as_of"), nullableLong(rs, "last_price_import_batch_id"),
                rs.getString("season"), read(rs.getString("tags_json"), new TypeReference<List<String>>() {}),
                rs.getString("main_image_key"),
                read(rs.getString("images_json"), new TypeReference<List<FashionProductImage>>() {}),
                rs.getLong("visual_version"), rs.getLong("product_row_version"), rs.getInt("available_qty"),
                instant(rs, "stock_as_of"), nullableLong(rs, "stock_batch_id"), rs.getLong("stock_row_version"));
    }

    private SelectionComboView mapCombo(ResultSet rs, int rowNum) throws SQLException {
        return new SelectionComboView(Long.toString(rs.getLong("id")), Long.toString(rs.getLong("quote_id")),
                rs.getString("combo_no"), rs.getString("name"), rs.getInt("category_count"),
                rs.getInt("set_qty"), rs.getInt("selected") == 1, rs.getInt("sort_no"), rs.getString("reason"),
                read(rs.getString("lock_json"), new TypeReference<List<String>>() {}),
                rs.getString("visual_hash"), rs.getLong("row_version"), List.of());
    }

    private static SelectionComboView withDetails(SelectionComboView combo, List<SelectionDetailView> details) {
        return new SelectionComboView(combo.id(), combo.quoteId(), combo.comboNo(), combo.name(),
                combo.categoryCount(), combo.setQty(), combo.selected(), combo.sortNo(), combo.reason(),
                combo.lockedSlots(), combo.visualHash(), combo.rowVersion(), details);
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("选品 JSON 数据无效", exception);
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
