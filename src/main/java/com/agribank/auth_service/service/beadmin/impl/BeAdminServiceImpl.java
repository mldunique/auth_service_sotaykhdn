package com.agribank.auth_service.service.beadmin.impl;

import com.agribank.auth_service.client.MockBEAdminClient;
import com.agribank.auth_service.client.RestBEAdminClient;
import com.agribank.auth_service.dto.request.LoginRequest;
import com.agribank.auth_service.dto.response.UserInfo;
import com.agribank.auth_service.service.beadmin.BeAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of BeAdminService.
 * Supports dynamic runtime triggering and configuration property switching between Mock & Rest BEAdmin clients.
 */
@Service
public class BeAdminServiceImpl implements BeAdminService {

    private static final Logger log = LoggerFactory.getLogger(BeAdminServiceImpl.class);

    private final MockBEAdminClient mockBEAdminClient;
    private final RestBEAdminClient restBEAdminClient;
    private final AtomicBoolean mockEnabled;

    public BeAdminServiceImpl(
            MockBEAdminClient mockBEAdminClient,
            RestBEAdminClient restBEAdminClient,
            @Value("${app.beadmin.mock:true}") boolean initialMockEnabled) {
        this.mockBEAdminClient = mockBEAdminClient;
        this.restBEAdminClient = restBEAdminClient;
        this.mockEnabled = new AtomicBoolean(initialMockEnabled);
        log.info("BeAdminServiceImpl initialized with Mock Mode = {}", initialMockEnabled);
    }

    @Override
    public boolean isMockEnabled() {
        return mockEnabled.get();
    }

    @Override
    public void setMockEnabled(boolean enabled) {
        log.info("BEAdmin Mock mode toggled to: {}", enabled);
        this.mockEnabled.set(enabled);
    }

    @Override
    public UserInfo authenticate(LoginRequest request) {
        if (mockEnabled.get()) {
            log.info("BEAdmin Mock Trigger ON: Authenticating via MockBEAdminClient for user: {}", request.getUsername());
            return mockBEAdminClient.authenticate(request);
        }
        log.info("BEAdmin Mock Trigger OFF: Authenticating via RestBEAdminClient for user: {}", request.getUsername());
        return restBEAdminClient.authenticate(request);
    }
}
