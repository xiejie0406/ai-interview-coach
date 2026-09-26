package com.ruoyi.aden.migration;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdenDatabasePreconditionsTest {
    @Test void currentSchemaAcceptsCollectionTablesAndStillRejectsMissingOrUnexpectedTables() throws Exception {
        List<String> current=new ArrayList<>(AdenDatabasePreconditions.EXPECTED_BUSINESS_TABLES);
        assertDoesNotThrow(()->AdenDatabasePreconditions.verifyCurrentTables(connection(current),"aden_test_guard"));
        List<String> old=current.stream().filter(table->!table.startsWith("aden_collection_")).toList();
        var missing=assertThrows(IllegalStateException.class,()->AdenDatabasePreconditions.verifyCurrentTables(connection(old),"aden_test_guard"));
        assertTrue(missing.getMessage().contains("aden_collection_snapshot"));
        List<String> rogue=new ArrayList<>(current); rogue.add("aden_unapproved_table");
        var unexpected=assertThrows(IllegalStateException.class,()->AdenDatabasePreconditions.verifyCurrentTables(connection(rogue),"aden_test_guard"));
        assertTrue(unexpected.getMessage().contains("aden_unapproved_table"));
    }
    private Connection connection(List<String> tables) throws Exception {
        Connection connection=mock(Connection.class); PreparedStatement statement=mock(PreparedStatement.class); ResultSet result=mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement); when(statement.executeQuery()).thenReturn(result);
        AtomicInteger index=new AtomicInteger(-1); when(result.next()).thenAnswer(invocation->index.incrementAndGet()<tables.size());
        when(result.getString(1)).thenAnswer(invocation->tables.get(index.get())); return connection;
    }
}
