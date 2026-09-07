package com.agribank.auth_service.service.database.impl;

import com.agribank.auth_service.dto.request.DatabaseCleanRequest;
import com.agribank.auth_service.dto.response.DatabaseCleanResponse;
import com.agribank.auth_service.dto.response.TableOperationResult;
import com.agribank.auth_service.service.database.DatabaseCleanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseCleanServiceImpl implements DatabaseCleanService {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    /**
     * Whitelist pattern for SQL identifier names.
     * Only alphanumeric characters, _, #, $ are allowed to prevent SQL Injection.
     */
    private static final Pattern VALID_TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_#$]+$");

    private record ForeignKeyRef(String childTable, String constraintName) {}

    @Override
    public DatabaseCleanResponse cleanDatabase(DatabaseCleanRequest request) {
        String dbProduct = getDatabaseProductName();
        log.info("Starting database cleanup on DB type: {}", dbProduct);

        List<TableOperationResult> truncateResults = new ArrayList<>();
        List<TableOperationResult> dropResults = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        List<String> truncateTables = request.getTruncateTables();
        List<String> dropTables = request.getDropTables();

        // 1. Process TRUNCATE requests
        for (String rawName : truncateTables) {
            if (rawName == null || rawName.trim().isEmpty()) {
                continue;
            }
            String tableName = rawName.trim();
            TableOperationResult result = truncateSingleTable(tableName, dbProduct);
            truncateResults.add(result);
            if ("FAILED".equals(result.getStatus())) {
                errors.add("Lỗi TRUNCATE bảng " + tableName + ": " + result.getMessage());
            }
        }

        // 2. Process DROP requests (DELETE TABLE IF EXISTS)
        for (String rawName : dropTables) {
            if (rawName == null || rawName.trim().isEmpty()) {
                continue;
            }
            String tableName = rawName.trim();
            TableOperationResult result = dropSingleTable(tableName, dbProduct);
            dropResults.add(result);
            if ("FAILED".equals(result.getStatus())) {
                errors.add("Lỗi DROP bảng " + tableName + ": " + result.getMessage());
            }
        }

        boolean allSuccess = errors.isEmpty();
        String summaryMessage;
        if (truncateResults.isEmpty() && dropResults.isEmpty()) {
            summaryMessage = "Không có bảng nào được yêu cầu xử lý (truncateTables và dropTables đều trống).";
        } else if (allSuccess) {
            summaryMessage = String.format("Dọn dẹp database thành công! (TRUNCATE: %d bảng, DROP: %d bảng).",
                    truncateResults.size(), dropResults.size());
        } else {
            summaryMessage = String.format("Hoàn thành với %d lỗi trong quá trình xử lý.", errors.size());
        }

        return DatabaseCleanResponse.builder()
                .success(allSuccess)
                .message(summaryMessage)
                .databaseProduct(dbProduct)
                .totalTruncateRequested(truncateResults.size())
                .totalDropRequested(dropResults.size())
                .truncateResults(truncateResults)
                .dropResults(dropResults)
                .errors(errors)
                .build();
    }

    private TableOperationResult truncateSingleTable(String tableName, String dbProduct) {
        if (!isValidTableName(tableName)) {
            return TableOperationResult.builder()
                    .tableName(tableName)
                    .operation("TRUNCATE")
                    .status("FAILED")
                    .message("Tên bảng không hợp lệ (chỉ chấp nhận ký tự a-z, A-Z, 0-9, _, #, $).")
                    .build();
        }

        boolean isOracle = dbProduct.toLowerCase().contains("oracle");
        boolean isMysql = dbProduct.toLowerCase().contains("mysql");

        try {
            // Find actual table name in DB dictionary (handles case-sensitivity, e.g. "comment" vs "COMMENT")
            Optional<String> actualTableNameOpt = findActualTableName(tableName, isOracle);
            if (actualTableNameOpt.isEmpty()) {
                return TableOperationResult.builder()
                        .tableName(tableName)
                        .operation("TRUNCATE")
                        .status("SKIPPED")
                        .message("Bảng không tồn tại trong cơ sở dữ liệu.")
                        .build();
            }

            String actualTableName = actualTableNameOpt.get();
            List<ForeignKeyRef> referencingFks = new ArrayList<>();

            // In Oracle, tables with referencing FKs cannot be truncated (ORA-02266).
            // We temporarily disable referencing FKs, perform truncate/delete, and re-enable them.
            if (isOracle) {
                referencingFks = getReferencingForeignKeys(actualTableName);
                for (ForeignKeyRef fk : referencingFks) {
                    try {
                        jdbcTemplate.execute("ALTER TABLE \"" + fk.childTable() + "\" DISABLE CONSTRAINT \"" + fk.constraintName() + "\"");
                        log.info("Disabled FK constraint \"{}\" on table \"{}\" referencing \"{}\"",
                                fk.constraintName(), fk.childTable(), actualTableName);
                    } catch (Exception ex) {
                        log.warn("Could not disable FK constraint \"{}\" on table \"{}\": {}",
                                fk.constraintName(), fk.childTable(), extractRootErrorMessage(ex));
                    }
                }
            }

            try {
                if (isMysql) {
                    jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
                    try {
                        jdbcTemplate.execute("TRUNCATE TABLE `" + actualTableName + "`");
                    } finally {
                        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                    }
                } else if (isOracle) {
                    try {
                        jdbcTemplate.execute("TRUNCATE TABLE \"" + actualTableName + "\"");
                    } catch (Exception ex) {
                        log.warn("TRUNCATE TABLE \"{}\" failed ({}). Falling back to DELETE FROM...",
                                actualTableName, extractRootErrorMessage(ex));
                        int deletedCount = jdbcTemplate.update("DELETE FROM \"" + actualTableName + "\"");
                        return TableOperationResult.builder()
                                .tableName(tableName)
                                .operation("TRUNCATE (DELETE FALLBACK)")
                                .status("SUCCESS")
                                .message("Đã xóa toàn bộ dữ liệu bằng lệnh DELETE do TRUNCATE bị hạn chế (Số dòng xóa: " + deletedCount + ").")
                                .build();
                    }
                } else {
                    jdbcTemplate.execute("TRUNCATE TABLE \"" + actualTableName + "\"");
                }

                log.info("Successfully truncated table: {}", actualTableName);
                return TableOperationResult.builder()
                        .tableName(tableName)
                        .operation("TRUNCATE")
                        .status("SUCCESS")
                        .message("Đã TRUNCATE toàn bộ dữ liệu thành công.")
                        .build();

            } finally {
                // Always re-enable referencing constraints in Oracle
                if (isOracle && !referencingFks.isEmpty()) {
                    for (ForeignKeyRef fk : referencingFks) {
                        try {
                            jdbcTemplate.execute("ALTER TABLE \"" + fk.childTable() + "\" ENABLE NOVALIDATE CONSTRAINT \"" + fk.constraintName() + "\"");
                            log.info("Re-enabled FK constraint \"{}\" on table \"{}\"",
                                    fk.constraintName(), fk.childTable());
                        } catch (Exception ex) {
                            log.warn("Could not re-enable FK constraint \"{}\" on table \"{}\": {}",
                                    fk.constraintName(), fk.childTable(), extractRootErrorMessage(ex));
                        }
                    }
                }
            }

        } catch (Exception e) {
            String rootError = extractRootErrorMessage(e);
            log.error("Failed to truncate table {}: {}", tableName, rootError);
            return TableOperationResult.builder()
                    .tableName(tableName)
                    .operation("TRUNCATE")
                    .status("FAILED")
                    .message(rootError)
                    .build();
        }
    }

    private TableOperationResult dropSingleTable(String tableName, String dbProduct) {
        if (!isValidTableName(tableName)) {
            return TableOperationResult.builder()
                    .tableName(tableName)
                    .operation("DROP")
                    .status("FAILED")
                    .message("Tên bảng không hợp lệ (chỉ chấp nhận ký tự a-z, A-Z, 0-9, _, #, $).")
                    .build();
        }

        boolean isOracle = dbProduct.toLowerCase().contains("oracle");
        boolean isMysql = dbProduct.toLowerCase().contains("mysql");

        try {
            Optional<String> actualTableNameOpt = findActualTableName(tableName, isOracle);
            if (actualTableNameOpt.isEmpty()) {
                return TableOperationResult.builder()
                        .tableName(tableName)
                        .operation("DROP")
                        .status("SKIPPED")
                        .message("Bảng không tồn tại (IF EXISTS: Bỏ qua).")
                        .build();
            }

            String actualTableName = actualTableNameOpt.get();

            if (isOracle) {
                // In Oracle, CASCADE CONSTRAINTS drops referencing FKs, PURGE prevents RecycleBin junk
                jdbcTemplate.execute("DROP TABLE \"" + actualTableName + "\" CASCADE CONSTRAINTS PURGE");
            } else if (isMysql) {
                jdbcTemplate.execute("DROP TABLE IF EXISTS `" + actualTableName + "`");
            } else {
                jdbcTemplate.execute("DROP TABLE \"" + actualTableName + "\"");
            }

            log.info("Successfully dropped table: {}", actualTableName);
            return TableOperationResult.builder()
                    .tableName(tableName)
                    .operation("DROP")
                    .status("SUCCESS")
                    .message("Đã xóa bảng thành công (DROP TABLE).")
                    .build();

        } catch (Exception e) {
            String rootError = extractRootErrorMessage(e);
            log.error("Failed to drop table {}: {}", tableName, rootError);
            return TableOperationResult.builder()
                    .tableName(tableName)
                    .operation("DROP")
                    .status("FAILED")
                    .message(rootError)
                    .build();
        }
    }

    /**
     * Finds the exact table name in the database dictionary case-insensitively.
     * Supports both lowercase (e.g. "comment") and uppercase ("BUSINESS").
     */
    private Optional<String> findActualTableName(String tableName, boolean isOracle) {
        if (isOracle) {
            try {
                // Check user_tables with case-insensitive comparison
                List<String> list = jdbcTemplate.query(
                        "SELECT table_name FROM user_tables WHERE UPPER(table_name) = UPPER(?)",
                        (rs, rowNum) -> rs.getString("table_name"),
                        tableName
                );
                if (!list.isEmpty()) {
                    return Optional.of(list.get(0));
                }

                // Fallback to all_tables in current schema
                List<String> allList = jdbcTemplate.query(
                        "SELECT table_name FROM all_tables WHERE UPPER(table_name) = UPPER(?) " +
                        "AND owner = (SELECT sys_context('USERENV', 'CURRENT_SCHEMA') FROM dual)",
                        (rs, rowNum) -> rs.getString("table_name"),
                        tableName
                );
                if (!allList.isEmpty()) {
                    return Optional.of(allList.get(0));
                }
            } catch (Exception e) {
                log.warn("Error querying Oracle tables for {}: {}", tableName, extractRootErrorMessage(e));
                // On query error, allow execution attempt with uppercase name
                return Optional.of(tableName.toUpperCase());
            }
            return Optional.empty();
        } else {
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                for (String testName : List.of(tableName, tableName.toUpperCase(), tableName.toLowerCase())) {
                    try (ResultSet rs = meta.getTables(null, null, testName, new String[]{"TABLE"})) {
                        if (rs.next()) {
                            return Optional.of(rs.getString("TABLE_NAME"));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error querying metadata tables for {}: {}", tableName, extractRootErrorMessage(e));
                return Optional.of(tableName);
            }
            return Optional.empty();
        }
    }

    /**
     * In Oracle, finds all foreign key constraints that reference the specified table.
     */
    private List<ForeignKeyRef> getReferencingForeignKeys(String actualTableName) {
        List<ForeignKeyRef> list = new ArrayList<>();
        try {
            String sql = "SELECT a.table_name, a.constraint_name " +
                    "FROM user_constraints a " +
                    "JOIN user_constraints b ON a.r_constraint_name = b.constraint_name " +
                    "WHERE UPPER(b.table_name) = UPPER(?) AND a.constraint_type = 'R' AND a.status = 'ENABLED'";
            list = jdbcTemplate.query(sql, (rs, rowNum) -> new ForeignKeyRef(
                    rs.getString("table_name"),
                    rs.getString("constraint_name")
            ), actualTableName);
        } catch (Exception e) {
            log.warn("Could not query referencing foreign keys from user_constraints for {}: {}",
                    actualTableName, extractRootErrorMessage(e));
        }

        if (list.isEmpty()) {
            try {
                String allSql = "SELECT a.table_name, a.constraint_name " +
                        "FROM all_constraints a " +
                        "JOIN all_constraints b ON a.r_constraint_name = b.constraint_name AND a.r_owner = b.owner " +
                        "WHERE UPPER(b.table_name) = UPPER(?) AND a.constraint_type = 'R' AND a.status = 'ENABLED' " +
                        "AND a.owner = (SELECT sys_context('USERENV', 'CURRENT_SCHEMA') FROM dual)";
                list = jdbcTemplate.query(allSql, (rs, rowNum) -> new ForeignKeyRef(
                        rs.getString("table_name"),
                        rs.getString("constraint_name")
                ), actualTableName);
            } catch (Exception e) {
                log.debug("all_constraints query ignored: {}", e.getMessage());
            }
        }
        return list;
    }

    @Override
    public Map<String, Object> getAvailableTables() {
        String dbProduct = getDatabaseProductName();
        boolean isOracle = dbProduct.toLowerCase().contains("oracle");
        List<String> tables = new ArrayList<>();

        try {
            if (isOracle) {
                tables = jdbcTemplate.query(
                        "SELECT table_name FROM user_tables ORDER BY table_name",
                        (rs, rowNum) -> rs.getString("table_name")
                );
            } else {
                try (Connection conn = dataSource.getConnection()) {
                    DatabaseMetaData meta = conn.getMetaData();
                    try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                        while (rs.next()) {
                            tables.add(rs.getString("TABLE_NAME"));
                        }
                    }
                }
                Collections.sort(tables);
            }
        } catch (Exception e) {
            log.error("Error retrieving table list: {}", extractRootErrorMessage(e));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("databaseProduct", dbProduct);
        response.put("totalTables", tables.size());
        response.put("tables", tables);
        return response;
    }

    private boolean isValidTableName(String tableName) {
        return tableName != null && VALID_TABLE_NAME_PATTERN.matcher(tableName).matches();
    }

    private String getDatabaseProductName() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            log.warn("Could not determine database product name: {}", e.getMessage());
            return "UNKNOWN";
        }
    }

    private String extractRootErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : throwable.getMessage();
    }
}
