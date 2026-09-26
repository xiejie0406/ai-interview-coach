package com.ruoyi.aps.infrastructure.mysql.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * 以枚举常量名持久化，禁止依赖 ordinal。
 */
public class ApsEnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E>
{
    private final Class<E> type;

    public ApsEnumTypeHandler(Class<E> type)
    {
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType)
            throws SQLException
    {
        if (jdbcType == null)
        {
            ps.setString(i, parameter.name());
        }
        else
        {
            ps.setObject(i, parameter.name(), jdbcType.TYPE_CODE);
        }
    }

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException
    {
        return parse(rs.getString(columnName));
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException
    {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException
    {
        return parse(cs.getString(columnIndex));
    }

    private E parse(String value) throws SQLException
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            return Enum.valueOf(type, value);
        }
        catch (IllegalArgumentException exception)
        {
            throw new SQLException("数据库枚举值不在 " + type.getSimpleName() + " 中: " + value,
                    "22000", exception);
        }
    }
}
