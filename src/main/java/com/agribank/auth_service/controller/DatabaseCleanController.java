package com.agribank.auth_service.controller;

import com.agribank.auth_service.dto.request.DatabaseCleanRequest;
import com.agribank.auth_service.dto.response.DatabaseCleanResponse;
import com.agribank.auth_service.service.database.DatabaseCleanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller providing independent database maintenance endpoints for UAT/testing environments.
 * Can be called directly via Postman without UI.
 */
@RestController
@RequestMapping("/api/v1/database")
@RequiredArgsConstructor
@Slf4j
public class DatabaseCleanController {

    private final DatabaseCleanService databaseCleanService;

    /**
     * POST /api/v1/database/clean
     * Truncates data in requested tables and/or drops requested tables if they exist.
     *
     * Sample JSON Body:
     * {
     *   "truncateTables": ["BRANCH", "AUDIT_LOG"],
     *   "dropTables": ["TEMP_TABLE_1", "TEMP_TABLE_2"]
     * }
     */
    @PostMapping({"/clean", "/cleanup"})
    public ResponseEntity<DatabaseCleanResponse> cleanDatabase(@RequestBody DatabaseCleanRequest request) {
        log.info("Received database clean request: truncate={}, drop={}",
                request.getTruncateTables(), request.getDropTables());

        DatabaseCleanResponse response = databaseCleanService.cleanDatabase(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/database/tables
     * Lists all existing tables in the connected database schema.
     * Helpful for inspecting which tables exist in UAT before running clean operations.
     */
    @GetMapping("/tables")
    public ResponseEntity<Map<String, Object>> getAvailableTables() {
        Map<String, Object> tables = databaseCleanService.getAvailableTables();
        return ResponseEntity.ok(tables);
    }
}
