package com.ruoyi.aden.infrastructure.time;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;

/** MyBatis DATETIME(6) handler：不依赖 JVM 或 JDBC session 的默认时区。 */
public final class AdenUtcInstantTypeHandler extends BaseTypeHandler<Instant> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Instant parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setObject(index, AdenUtcDateTimeCodec.toDatabase(parameter));
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return decode(resultSet.getObject(columnName, LocalDateTime.class));
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return decode(resultSet.getObject(columnIndex, LocalDateTime.class));
    }

    @Override
    public Instant getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return decode(statement.getObject(columnIndex, LocalDateTime.class));
    }

    private static Instant decode(LocalDateTime value) {
        return value == null ? null : AdenUtcDateTimeCodec.fromDatabase(value);
    }
}
