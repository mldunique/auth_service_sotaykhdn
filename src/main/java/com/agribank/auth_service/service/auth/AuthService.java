package com.agribank.auth_service.service.auth;

import com.agribank.auth_service.dto.request.LoginRequest;
import com.agribank.auth_service.dto.response.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

}