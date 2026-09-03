package com.agribank.auth_service.service.beadmin;

import com.agribank.auth_service.dto.request.LoginRequest;
import com.agribank.auth_service.dto.response.UserInfo;

public interface BeAdminService {

    UserInfo authenticate(LoginRequest request);

    boolean isMockEnabled();

    void setMockEnabled(boolean enabled);
}
