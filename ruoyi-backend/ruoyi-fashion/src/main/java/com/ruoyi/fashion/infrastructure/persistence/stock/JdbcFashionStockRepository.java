package com.ruoyi.fashion.infrastructure.persistence.stock;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ruoyi.fashion.application.stock.port.FashionStockRepository;
import com.ruoyi.fashion.domain.stock.FashionStock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@FashionModuleEnabled
@Repository
public class JdbcFashionStockRepository implements FashionStockRepository {
    private static final String COLUMNS = """
            id,product_id,warehouse_code,available_qty,as_of,confirmation_type,verified_by,evidence_note,
            last_import_batch_id,create_by,create_time,update_by,update_time,row_version
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<FashionStock> rowMapper = this::map;

    public JdbcFashionStockRepository(@Qualifier("fashionJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, FashionStock> findByProductIds(Collection<Long> productIds, String warehouseCode) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<FashionStock> rows = jdbc.query(
                "select " + COLUMNS + " from fq_stock where product_id in (:ids) and warehouse_code=:warehouse",
                new MapSqlParameterSource().addValue("ids", productIds).addValue("warehouse", warehouseCode),
                rowMapper);
        Map<Long, FashionStock> result = new LinkedHashMap<>();
        rows.forEach(stock -> result.put(stock.productId(), stock));
        return result;
    }

    @Override
    public void insert(FashionStock stock) {
        int changed = jdbc.update("""
                insert into fq_stock (
                    id,product_id,warehouse_code,available_qty,as_of,confirmation_type,verified_by,evidence_note,
                    last_import_batch_id,create_by,create_time,update_by,update_time,row_version)
                values (
                    :id,:productId,:warehouse,:qty,:asOf,:type,:verifiedBy,:evidence,:batchId,
                    :createBy,:createTime,:updateBy,:updateTime,:rowVersion)
                """, parameters(stock));
        if (changed != 1) {
            throw new IllegalStateException("新增库存未写入一行");
        }
    }

    @Override
    public boolean update(
            long id,
            int availableQty,
            Instant asOf,
            String confirmationType,
            Long verifiedBy,
            String evidenceNote,
            long batchId,
            long expectedRowVersion,
            long operatorId,
            Instant now) {
        return jdbc.update("""
                update fq_stock set available_qty=:qty,as_of=:asOf,confirmation_type=:type,
                    verified_by=:verifiedBy,evidence_note=:evidence,last_import_batch_id=:batchId,
                    update_by=:operator,update_time=:now,row_version=row_version+1
                 where id=:id and row_version=:version
                """, new MapSqlParameterSource().addValue("qty", availableQty)
                .addValue("asOf", Timestamp.from(asOf)).addValue("type", confirmationType)
                .addValue("verifiedBy", verifiedBy).addValue("evidence", evidenceNote)
                .addValue("batchId", batchId).addValue("operator", operatorId)
                .addValue("now", Timestamp.from(now)).addValue("id", id)
                .addValue("version", expectedRowVersion)) == 1;
    }

    @Override
    public boolean delete(long id, long expectedRowVersion) {
        return jdbc.update("delete from fq_stock where id=:id and row_version=:version",
                Map.of("id", id, "version", expectedRowVersion)) == 1;
    }

    private MapSqlParameterSource parameters(FashionStock s) {
        return new MapSqlParameterSource().addValue("id", s.id()).addValue("productId", s.productId())
                .addValue("warehouse", s.warehouseCode()).addValue("qty", s.availableQty())
                .addValue("asOf", Timestamp.from(s.asOf())).addValue("type", s.confirmationType())
                .addValue("verifiedBy", s.verifiedBy()).addValue("evidence", s.evidenceNote())
                .addValue("batchId", s.lastImportBatchId()).addValue("createBy", s.createBy())
                .addValue("createTime", Timestamp.from(s.createTime())).addValue("updateBy", s.updateBy())
                .addValue("updateTime", Timestamp.from(s.updateTime())).addValue("rowVersion", s.rowVersion());
    }

    private FashionStock map(ResultSet rs, int rowNum) throws SQLException {
        return new FashionStock(
                rs.getLong("id"), rs.getLong("product_id"), rs.getString("warehouse_code"),
                rs.getInt("available_qty"), rs.getTimestamp("as_of").toInstant(),
                rs.getString("confirmation_type"), nullableLong(rs, "verified_by"), rs.getString("evidence_note"),
                rs.getLong("last_import_batch_id"), rs.getLong("create_by"),
                rs.getTimestamp("create_time").toInstant(), rs.getLong("update_by"),
                rs.getTimestamp("update_time").toInstant(), rs.getLong("row_version"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
