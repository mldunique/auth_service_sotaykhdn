package com.agribank.auth_service.service.beadmin.impl;

import com.agribank.auth_service.client.BEAdminClient;
import com.agribank.auth_service.dto.request.LoginRequest;
import com.agribank.auth_service.dto.response.UserInfo;
import com.agribank.auth_service.service.beadmin.BeAdminService;
import org.springframework.stereotype.Service;

/**
 * Implementation of BeAdminService.
 * Delegates communication with external system to the active client bean (Mock or Rest).
 */
@Service
public class BeAdminServiceImpl implements BeAdminService {

    private final BEAdminClient beAdminClient;

    public BeAdminServiceImpl(BEAdminClient beAdminClient) {
        this.beAdminClient = beAdminClient;
    }


    @Override
    public UserInfo authenticate(LoginRequest request) {
        return beAdminClient.authenticate(request);
    }
}
