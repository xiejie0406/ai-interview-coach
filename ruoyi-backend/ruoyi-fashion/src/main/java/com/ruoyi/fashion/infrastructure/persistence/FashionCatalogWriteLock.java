package com.ruoyi.fashion.infrastructure.persistence;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** MySQL catalog-write 命名锁；必须在 Fashion 事务中使用，且在事务完成后由同一连接释放。 */
@FashionModuleEnabled
@Component
public final class FashionCatalogWriteLock {
    private static final String DATABASE_SQL = "select database()";
    private static final String ACQUIRE_SQL = "select get_lock(?, ?)";
    private static final String RELEASE_SQL = "select release_lock(?)";

    private final DataSource dataSource;

    public FashionCatalogWriteLock(@Qualifier("dynamicDataSource") DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public <T> T executeLocked(int timeoutSeconds, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        if (timeoutSeconds < 0 || timeoutSeconds > 60) {
            throw new IllegalArgumentException("命名锁等待时间必须在 0～60 秒之间");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("FashionCatalogWriteLock 必须在 fashionTransactionManager 事务内调用");
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);
        String lockName = lockName(connection);
        acquire(connection, lockName, timeoutSeconds);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public int getOrder() {
                // 必须先于 DataSourceUtils 将事务连接归还连接池。
                return DataSourceUtils.CONNECTION_SYNCHRONIZATION_ORDER - 1;
            }

            @Override
            public void afterCompletion(int status) {
                release(connection, lockName);
            }
        });
        return action.get();
    }

    static String lockName(Connection connection) {
        String database = queryString(connection, DATABASE_SQL);
        if (database == null || database.isBlank()) {
            throw new IllegalStateException("当前 MySQL 连接未选择数据库");
        }
        String name = "fashion:" + database + ":catalog-write";
        if (name.length() > 64) {
            throw new IllegalStateException("Fashion 命名锁超过 MySQL 64 字符限制");
        }
        return name;
    }

    private static void acquire(Connection connection, String lockName, int timeoutSeconds) {
        Integer result = queryInteger(connection, ACQUIRE_SQL, lockName, timeoutSeconds);
        if (result == null) {
            throw new IllegalStateException("MySQL GET_LOCK 执行失败");
        }
        if (result != 1) {
            throw new FashionCatalogWriteLockTimeoutException(lockName, timeoutSeconds);
        }
    }

    private static void release(Connection connection, String lockName) {
        Integer result = queryInteger(connection, RELEASE_SQL, lockName);
        if (result == null || result != 1) {
            throw new IllegalStateException("MySQL RELEASE_LOCK 未释放当前连接持有的锁 " + lockName);
        }
    }

    private static String queryString(Connection connection, String sql) {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
        } catch (SQLException exception) {
            throw new IllegalStateException("执行 MySQL 命名锁查询失败", exception);
        }
    }

    private static Integer queryInteger(Connection connection, String sql, Object... arguments) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                Object value = result.getObject(1);
                return value == null ? null : ((Number) value).intValue();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("执行 MySQL 命名锁查询失败", exception);
        }
    }
}
