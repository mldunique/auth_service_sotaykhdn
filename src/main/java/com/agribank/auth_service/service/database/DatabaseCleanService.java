package com.agribank.auth_service.service.database;

import com.agribank.auth_service.dto.request.DatabaseCleanRequest;
import com.agribank.auth_service.dto.response.DatabaseCleanResponse;

import java.util.Map;

public interface DatabaseCleanService {

    /**
     * Executes TRUNCATE on specified tables and DROP on specified tables.
     *
     * @param request DatabaseCleanRequest containing list of tables to truncate and drop.
     * @return Detailed DatabaseCleanResponse with per-table results.
     */
    DatabaseCleanResponse cleanDatabase(DatabaseCleanRequest request);

    /**
     * Retrieves the list of existing tables in the connected database schema.
     *
     * @return Map containing database info and table list.
     */
    Map<String, Object> getAvailableTables();
}
