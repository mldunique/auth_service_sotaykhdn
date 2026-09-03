package com.agribank.auth_service.controller;

import com.agribank.auth_service.client.MockBEAdminClient;
import com.agribank.auth_service.service.beadmin.BeAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller to trigger/toggle Mock BEAdmin mode at runtime and list test accounts.
 */
@RestController
@RequestMapping("/api/v1/mock-beadmin")
public class MockBEAdminController {

    private final BeAdminService beAdminService;
    private final MockBEAdminClient mockBEAdminClient;

    public MockBEAdminController(BeAdminService beAdminService, MockBEAdminClient mockBEAdminClient) {
        this.beAdminService = beAdminService;
        this.mockBEAdminClient = mockBEAdminClient;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("mockEnabled", beAdminService.isMockEnabled());
        response.put("message", beAdminService.isMockEnabled() ? "Mock BEAdmin Mode is ACTIVE" : "Normal Live BEAdmin Mode is ACTIVE");
        response.put("mockUsers", mockBEAdminClient.getMockUsers());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleMock(@RequestParam(name = "enabled", required = false) Boolean enabled) {
        boolean newStatus = (enabled != null) ? enabled : !beAdminService.isMockEnabled();
        beAdminService.setMockEnabled(newStatus);

        Map<String, Object> response = new HashMap<>();
        response.put("mockEnabled", newStatus);
        response.put("message", newStatus ? "Mock BEAdmin Mode activated successfully" : "Normal Live BEAdmin Mode restored successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/enable")
    public ResponseEntity<Map<String, Object>> enableMock() {
        return toggleMock(true);
    }

    @PostMapping("/disable")
    public ResponseEntity<Map<String, Object>> disableMock() {
        return toggleMock(false);
    }
}
