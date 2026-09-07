package com.agribank.auth_service.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request payload for cleaning database tables.
 * Supports flexible property names for convenient calling from Postman.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatabaseCleanRequest {

    /**
     * List of table names whose data should be truncated (TRUNCATE).
     */
    @Builder.Default
    @JsonProperty("truncateTables")
    @JsonAlias({"truncate", "truncate_tables", "tablesToTruncate", "tables"})
    private List<String> truncateTables = new ArrayList<>();

    /**
     * List of table names to be dropped/deleted (DROP TABLE IF EXISTS).
     */
    @Builder.Default
    @JsonProperty("dropTables")
    @JsonAlias({"deleteTables", "delete_tables", "drop", "delete", "tablesToDrop", "tablesToDelete"})
    private List<String> dropTables = new ArrayList<>();

    public List<String> getTruncateTables() {
        return truncateTables != null ? truncateTables : new ArrayList<>();
    }

    public List<String> getDropTables() {
        return dropTables != null ? dropTables : new ArrayList<>();
    }
}
