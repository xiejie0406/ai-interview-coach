package com.ruoyi.aps.infrastructure.mysql.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.ruoyi.aps.domain.shared.ApsId;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class ApsIdTypeHandler extends BaseTypeHandler<ApsId>
{
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ApsId parameter, JdbcType jdbcType)
            throws SQLException
    {
        ps.setString(i, parameter.value());
    }

    @Override
    public ApsId getNullableResult(ResultSet rs, String columnName) throws SQLException
    {
        return toId(rs.getString(columnName));
    }

    @Override
    public ApsId getNullableResult(ResultSet rs, int columnIndex) throws SQLException
    {
        return toId(rs.getString(columnIndex));
    }

    @Override
    public ApsId getNullableResult(CallableStatement cs, int columnIndex) throws SQLException
    {
        return toId(cs.getString(columnIndex));
    }

    private ApsId toId(String value)
    {
        return value == null ? null : new ApsId(value);
    }
}
