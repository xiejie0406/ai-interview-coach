package com.ruoyi.aps.infrastructure.mysql.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * 在 JDBC 边界固定用 UTC 读写 MySQL DATETIME(3)。
 */
public class UtcInstantTypeHandler extends BaseTypeHandler<Instant>
{
    private static Calendar utcCalendar()
    {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType)
            throws SQLException
    {
        ps.setTimestamp(i, Timestamp.from(parameter), utcCalendar());
    }

    @Override
    public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException
    {
        return toInstant(rs.getTimestamp(columnName, utcCalendar()));
    }

    @Override
    public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException
    {
        return toInstant(rs.getTimestamp(columnIndex, utcCalendar()));
    }

    @Override
    public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException
    {
        return toInstant(cs.getTimestamp(columnIndex, utcCalendar()));
    }

    private Instant toInstant(Timestamp timestamp)
    {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
