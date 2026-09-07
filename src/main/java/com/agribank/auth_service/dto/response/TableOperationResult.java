package com.agribank.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableOperationResult {

    private String tableName;

    /**
     * Operation type: TRUNCATE, DROP
     */
    private String operation;

    /**
     * Status: SUCCESS, SKIPPED, FAILED
     */
    private String status;

    /**
     * Detailed status or error message
     */
    private String message;
}
