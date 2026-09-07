package com.agribank.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatabaseCleanResponse {

    private boolean success;

    private String message;

    private String databaseProduct;

    private int totalTruncateRequested;

    private int totalDropRequested;

    @Builder.Default
    private List<TableOperationResult> truncateResults = new ArrayList<>();

    @Builder.Default
    private List<TableOperationResult> dropResults = new ArrayList<>();

    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
