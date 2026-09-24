package com.ruoyi.fashion.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class FashionCatalogWriteLockTest {
    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement databaseStatement;
    private PreparedStatement acquireStatement;
    private PreparedStatement releaseStatement;
    private TransactionTemplate transaction;
    private FashionCatalogWriteLock lock;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        databaseStatement = scalarString("ry-vue");
        acquireStatement = scalarNumber(1);
        releaseStatement = scalarNumber(1);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.isClosed()).thenReturn(false);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "select database()" -> databaseStatement;
            case "select get_lock(?, ?)" -> acquireStatement;
            case "select release_lock(?)" -> releaseStatement;
            default -> throw new AssertionError("unexpected SQL: " + invocation.getArgument(0));
        });
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        lock = new FashionCatalogWriteLock(dataSource);
    }

    @Test
    void releasesOnSameConnectionOnlyAfterCommit() throws Exception {
        String result = transaction.execute(status -> lock.executeLocked(5, () -> "done"));

        assertEquals("done", result);
        InOrder order = inOrder(acquireStatement, connection, releaseStatement);
        order.verify(acquireStatement).executeQuery();
        order.verify(connection).commit();
        order.verify(releaseStatement).executeQuery();
    }

    @Test
    void releasesOnlyAfterRollback() throws Exception {
        assertThrows(IllegalStateException.class, () -> transaction.execute(status ->
                lock.executeLocked(5, () -> {
                    throw new IllegalStateException("business failure");
                })));

        InOrder order = inOrder(acquireStatement, connection, releaseStatement);
        order.verify(acquireStatement).executeQuery();
        order.verify(connection).rollback();
        order.verify(releaseStatement).executeQuery();
    }

    @Test
    void rejectsUseOutsideTransactionAndLockTimeout() throws Exception {
        assertThrows(IllegalStateException.class, () -> lock.executeLocked(5, () -> null));

        PreparedStatement timeout = scalarNumber(0);
        when(connection.prepareStatement("select get_lock(?, ?)")).thenReturn(timeout);
        assertThrows(FashionCatalogWriteLockTimeoutException.class,
                () -> transaction.execute(status -> lock.executeLocked(0, () -> null)));
    }

    private static PreparedStatement scalarString(String value) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn(value);
        return statement;
    }

    private static PreparedStatement scalarNumber(int value) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getObject(1)).thenReturn(value);
        return statement;
    }
}
