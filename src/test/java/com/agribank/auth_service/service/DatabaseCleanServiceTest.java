package com.agribank.auth_service.service;

import com.agribank.auth_service.dto.request.DatabaseCleanRequest;
import com.agribank.auth_service.dto.response.DatabaseCleanResponse;
import com.agribank.auth_service.dto.response.TableOperationResult;
import com.agribank.auth_service.service.database.impl.DatabaseCleanServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class DatabaseCleanServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    private DatabaseCleanServiceImpl databaseCleanService;

    @BeforeEach
    void setUp() throws SQLException {
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.getMetaData()).thenReturn(databaseMetaData);
        lenient().when(databaseMetaData.getDatabaseProductName()).thenReturn("Oracle");

        databaseCleanService = new DatabaseCleanServiceImpl(jdbcTemplate, dataSource);
    }

    @Test
    void testCleanDatabase_SuccessfulTruncateAndDrop() {
        // Mock table exists in USER_TABLES
        when(jdbcTemplate.query(
                eq("SELECT table_name FROM user_tables WHERE UPPER(table_name) = UPPER(?)"),
                any(RowMapper.class),
                eq("BRANCH")
        )).thenReturn(List.of("BRANCH"));

        when(jdbcTemplate.query(
                eq("SELECT table_name FROM user_tables WHERE UPPER(table_name) = UPPER(?)"),
                any(RowMapper.class),
                eq("TEMP_DATA")
        )).thenReturn(List.of("TEMP_DATA"));

        DatabaseCleanRequest request = DatabaseCleanRequest.builder()
                .truncateTables(Collections.singletonList("BRANCH"))
                .dropTables(Collections.singletonList("TEMP_DATA"))
                .build();

        DatabaseCleanResponse response = databaseCleanService.cleanDatabase(request);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getTotalTruncateRequested());
        assertEquals(1, response.getTotalDropRequested());
        assertEquals("SUCCESS", response.getTruncateResults().get(0).getStatus());
        assertEquals("SUCCESS", response.getDropResults().get(0).getStatus());

        verify(jdbcTemplate).execute("TRUNCATE TABLE \"BRANCH\"");
        verify(jdbcTemplate).execute("DROP TABLE \"TEMP_DATA\" CASCADE CONSTRAINTS PURGE");
    }

    @Test
    void testCleanDatabase_DropNonExistentTable_ShouldBeSkipped() {
        // Mock table does NOT exist in USER_TABLES or ALL_TABLES
        when(jdbcTemplate.query(
                eq("SELECT table_name FROM user_tables WHERE UPPER(table_name) = UPPER(?)"),
                any(RowMapper.class),
                eq("NON_EXISTENT")
        )).thenReturn(Collections.emptyList());

        when(jdbcTemplate.query(
                contains("SELECT table_name FROM all_tables"),
                any(RowMapper.class),
                eq("NON_EXISTENT")
        )).thenReturn(Collections.emptyList());

        DatabaseCleanRequest request = DatabaseCleanRequest.builder()
                .dropTables(Collections.singletonList("NON_EXISTENT"))
                .build();

        DatabaseCleanResponse response = databaseCleanService.cleanDatabase(request);

        assertTrue(response.isSuccess());
        assertEquals("SKIPPED", response.getDropResults().get(0).getStatus());
        assertTrue(response.getDropResults().get(0).getMessage().contains("IF EXISTS"));
    }

    @Test
    void testCleanDatabase_InvalidTableName_ShouldFailSafely() {
        DatabaseCleanRequest request = DatabaseCleanRequest.builder()
                .truncateTables(Collections.singletonList("USERS; DROP TABLE BRANCH;--"))
                .dropTables(Collections.singletonList("INVALID TABLE NAME!"))
                .build();

        DatabaseCleanResponse response = databaseCleanService.cleanDatabase(request);

        assertFalse(response.isSuccess());
        assertEquals(2, response.getErrors().size());
        assertEquals("FAILED", response.getTruncateResults().get(0).getStatus());
        assertEquals("FAILED", response.getDropResults().get(0).getStatus());
        assertTrue(response.getTruncateResults().get(0).getMessage().contains("không hợp lệ"));

        // Ensure no SQL was executed
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void testCleanDatabase_TruncateForeignKeyFallbackToDelete() {
        when(jdbcTemplate.query(
                eq("SELECT table_name FROM user_tables WHERE UPPER(table_name) = UPPER(?)"),
                any(RowMapper.class),
                eq("PARENT_TABLE")
        )).thenReturn(List.of("PARENT_TABLE"));

        // Simulate ORA-02266
        doThrow(new DataIntegrityViolationException("ORA-02266: unique/primary keys in table referenced by enabled foreign keys"))
                .when(jdbcTemplate).execute("TRUNCATE TABLE \"PARENT_TABLE\"");
        when(jdbcTemplate.update("DELETE FROM \"PARENT_TABLE\"")).thenReturn(5);

        DatabaseCleanRequest request = DatabaseCleanRequest.builder()
                .truncateTables(Collections.singletonList("PARENT_TABLE"))
                .build();

        DatabaseCleanResponse response = databaseCleanService.cleanDatabase(request);

        assertTrue(response.isSuccess());
        TableOperationResult result = response.getTruncateResults().get(0);
        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.getMessage().contains("DELETE"));
        verify(jdbcTemplate).update("DELETE FROM \"PARENT_TABLE\"");
    }
}
