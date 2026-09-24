package com.ruoyi.fashion.infrastructure.persistence.quote;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ruoyi.fashion.application.quote.ProductPriceFact;
import com.ruoyi.fashion.application.quote.port.FashionQuoteRepository;
import com.ruoyi.fashion.domain.quote.FashionQuote;
import com.ruoyi.fashion.infrastructure.persistence.foundation.FashionJsonCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFashionQuoteRepository implements FashionQuoteRepository {
    private static final String COLUMNS = "q.id,q.quote_no,q.version_no,q.source_quote_id,q.customer_id,"
            + "q.customer_name,q.title,q.salesperson_id,q.requirement_text,q.requirement_json,q.requirement_confirmed,"
            + "q.requested_qty,q.budget,q.budget_basis,q.quote_mode,q.progressive,q.combo_template_json,"
            + "q.warehouse_code,q.currency,q.tax_mode,q.presentation_json,q.status,q.create_by,q.create_time,"
            + "q.update_by,q.update_time,q.row_version";

    private final NamedParameterJdbcTemplate jdbc;
    private final FashionJsonCodec json;
    private final RowMapper<FashionQuote> mapper = this::map;

    public JdbcFashionQuoteRepository(
            @Qualifier("fashionJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            FashionJsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<FashionQuote> search(
            long userId, boolean administrator, String status, String keyword, int offset, int limit) {
        Query query = query(userId, administrator, status, keyword);
        query.parameters().addValue("offset", offset).addValue("limit", limit);
        return jdbc.query("select " + COLUMNS + " from fq_quote q join fq_customer c on c.id=q.customer_id "
                + query.where() + " order by q.update_time desc,q.id desc limit :limit offset :offset",
                query.parameters(), mapper);
    }

    @Override
    public long count(long userId, boolean administrator, String status, String keyword) {
        Query query = query(userId, administrator, status, keyword);
        Long value = jdbc.queryForObject("select count(*) from fq_quote q join fq_customer c on c.id=q.customer_id "
                + query.where(), query.parameters(), Long.class);
        return value == null ? 0 : value;
    }

    @Override
    public Optional<FashionQuote> findById(long id) {
        return jdbc.query("select " + COLUMNS + " from fq_quote q where q.id=:id", Map.of("id", id), mapper)
                .stream().findFirst();
    }

    @Override
    public void insert(FashionQuote q) {
        int changed = jdbc.update("""
                insert into fq_quote (
                    id,quote_no,version_no,source_quote_id,customer_id,customer_name,title,salesperson_id,
                    requirement_text,requirement_json,requirement_confirmed,requested_qty,budget,budget_basis,
                    quote_mode,progressive,combo_template_json,warehouse_code,currency,tax_mode,tax_rate,fee_taxable,
                    discount_type,discount_rate,fixed_discount,freight,subtotal,discount_amount,tax_amount,total_amount,
                    approval_json,valid_days,valid_until,public_note,presentation_json,content_hash,confirmed_by,
                    confirmed_at,status,void_reason,is_demo,create_by,create_time,update_by,update_time,row_version)
                values (
                    :id,:quoteNo,:versionNo,:sourceQuoteId,:customerId,:customerName,:title,:salesperson,
                    :requirementText,:requirementJson,:confirmed,:qty,:budget,:budgetBasis,:quoteMode,:progressive,
                    :comboTemplate,:warehouse,:currency,:taxMode,null,1,'percent',5.00,0,0,null,null,null,null,
                    null,7,null,null,:presentation,null,null,null,:status,null,0,:createBy,:createTime,:updateBy,
                    :updateTime,:rowVersion)
                """, parameters(q));
        if (changed != 1) {
            throw new IllegalStateException("新增方案未写入一行");
        }
    }

    @Override
    public boolean updateDraft(FashionQuote q, long expectedRowVersion) {
        return jdbc.update("""
                update fq_quote set customer_id=:customerId,customer_name=:customerName,title=:title,
                    requirement_text=:requirementText,requirement_json=:requirementJson,
                    requirement_confirmed=:confirmed,requested_qty=:qty,budget=:budget,budget_basis=:budgetBasis,
                    quote_mode=:quoteMode,progressive=:progressive,combo_template_json=:comboTemplate,
                    warehouse_code=:warehouse,currency=:currency,tax_mode=:taxMode,
                    subtotal=null,discount_amount=null,tax_amount=null,total_amount=null,approval_json=null,
                    valid_until=null,content_hash=null,confirmed_by=null,confirmed_at=null,
                    update_by=:updateBy,update_time=:updateTime,row_version=row_version+1
                 where id=:id and status='draft' and row_version=:expectedVersion
                """, parameters(q).addValue("expectedVersion", expectedRowVersion)) == 1;
    }

    @Override
    public boolean updateStatus(long id, String status, long expectedRowVersion, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote set status=:status,update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and status='draft' and row_version=:version
                """, new MapSqlParameterSource().addValue("id", id).addValue("status", status)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now))
                .addValue("version", expectedRowVersion)) == 1;
    }

    @Override
    public boolean touchDraft(long id, long expectedRowVersion, long operatorId, Instant now) {
        return jdbc.update("""
                update fq_quote set subtotal=null,discount_amount=null,tax_amount=null,total_amount=null,
                    content_hash=null,update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and status='draft' and row_version=:version
                """, new MapSqlParameterSource().addValue("id", id).addValue("version", expectedRowVersion)
                .addValue("operator", operatorId).addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public int nextVersion(String quoteNo) {
        Integer value = jdbc.queryForObject(
                "select coalesce(max(version_no),0)+1 from fq_quote where quote_no=:quoteNo for update",
                Map.of("quoteNo", quoteNo), Integer.class);
        return value == null ? 1 : value;
    }

    @Override
    public List<ProductPriceFact> findPriceFacts(long quoteId) {
        return jdbc.query("""
                select distinct p.id,p.sku_code,p.sale_price,p.currency,p.tax_mode,p.price_as_of,
                       p.last_price_import_batch_id,p.row_version
                  from fq_quote_combo c
                  join fq_quote_detail d on d.combo_id=c.id
                  join fq_product p on p.id=d.product_id
                 where c.quote_id=:quoteId
                 order by p.id
                """, Map.of("quoteId", quoteId), (rs, rowNum) -> {
                    Long batchId = nullableLong(rs, "last_price_import_batch_id");
                    java.math.BigDecimal price = rs.getBigDecimal("sale_price");
                    Instant priceAsOf = instant(rs, "price_as_of");
                    return new ProductPriceFact(Long.toString(rs.getLong("id")), rs.getString("sku_code"),
                            price, rs.getString("currency"), rs.getString("tax_mode"), priceAsOf,
                            batchId == null ? null : Long.toString(batchId), rs.getLong("row_version"),
                            price != null && price.signum() > 0 && priceAsOf != null && batchId != null);
                });
    }

    private Query query(long userId, boolean administrator, String status, String keyword) {
        ArrayList<String> clauses = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (!administrator) {
            clauses.add("(q.salesperson_id=:userId or c.salesperson_id=:userId "
                    + "or json_contains(c.collaborator_ids,cast(:userIdJson as json),'$'))");
            parameters.addValue("userId", userId).addValue("userIdJson", Long.toString(userId));
        }
        if (status != null && !status.isBlank()) {
            clauses.add("q.status=:status");
            parameters.addValue("status", status);
        }
        if (keyword != null && !keyword.isBlank()) {
            clauses.add("(q.quote_no like :keyword or q.title like :keyword or q.customer_name like :keyword)");
            parameters.addValue("keyword", "%" + keyword + "%");
        }
        return new Query(clauses.isEmpty() ? "" : "where " + String.join(" and ", clauses), parameters);
    }

    private MapSqlParameterSource parameters(FashionQuote q) {
        return new MapSqlParameterSource().addValue("id", q.id()).addValue("quoteNo", q.quoteNo())
                .addValue("versionNo", q.versionNo()).addValue("sourceQuoteId", q.sourceQuoteId())
                .addValue("customerId", q.customerId()).addValue("customerName", q.customerName())
                .addValue("title", q.title()).addValue("salesperson", q.salespersonId())
                .addValue("requirementText", q.requirementText()).addValue("requirementJson", json.write(q.requirementJson()))
                .addValue("confirmed", q.requirementConfirmed() ? 1 : 0).addValue("qty", q.requestedQty())
                .addValue("budget", q.budget()).addValue("budgetBasis", q.budgetBasis())
                .addValue("quoteMode", q.quoteMode()).addValue("progressive", q.progressive() ? 1 : 0)
                .addValue("comboTemplate", json.write(q.comboTemplateJson())).addValue("warehouse", q.warehouseCode())
                .addValue("currency", q.currency()).addValue("taxMode", q.taxMode())
                .addValue("presentation", json.write(q.presentationJson())).addValue("status", q.status())
                .addValue("createBy", q.createBy()).addValue("createTime", Timestamp.from(q.createTime()))
                .addValue("updateBy", q.updateBy()).addValue("updateTime", Timestamp.from(q.updateTime()))
                .addValue("rowVersion", q.rowVersion());
    }

    private FashionQuote map(ResultSet rs, int rowNum) throws SQLException {
        return new FashionQuote(rs.getLong("id"), rs.getString("quote_no"), rs.getInt("version_no"),
                nullableLong(rs, "source_quote_id"), rs.getLong("customer_id"), rs.getString("customer_name"),
                rs.getString("title"), rs.getLong("salesperson_id"), rs.getString("requirement_text"),
                json.readTree(rs.getString("requirement_json")), rs.getInt("requirement_confirmed") == 1,
                rs.getInt("requested_qty"), rs.getBigDecimal("budget"), rs.getString("budget_basis"),
                rs.getString("quote_mode"), rs.getInt("progressive") == 1,
                json.readTree(rs.getString("combo_template_json")), rs.getString("warehouse_code"),
                rs.getString("currency"), rs.getString("tax_mode"), json.readTree(rs.getString("presentation_json")),
                rs.getString("status"), rs.getLong("create_by"), instant(rs, "create_time"),
                rs.getLong("update_by"), instant(rs, "update_time"), rs.getLong("row_version"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record Query(String where, MapSqlParameterSource parameters) {
    }
}
