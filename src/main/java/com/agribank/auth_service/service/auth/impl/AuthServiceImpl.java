package com.agribank.auth_service.service.auth.impl;

import com.agribank.auth_service.dto.request.LoginRequest;
import com.agribank.auth_service.dto.response.LoginResponse;
import com.agribank.auth_service.dto.response.UserInfo;
import com.agribank.auth_service.security.JwtTokenProvider;
import com.agribank.auth_service.service.auth.AuthService;
import com.agribank.auth_service.service.beadmin.BeAdminService;
import org.springframework.stereotype.Service;
import com.agribank.auth_service.exception.AuthenticationException;

/**
 * Service implementation for managing the authentication process flow.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthServiceImpl.class);

    private final BeAdminService beAdminService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthServiceImpl(BeAdminService beAdminService, JwtTokenProvider jwtTokenProvider) {
        this.beAdminService = beAdminService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        try {
            UserInfo user = beAdminService.authenticate(request);

            if (user == null) {
                return LoginResponse.builder()
                        .success(false)
                        .message("Sai tài khoản hoặc mật khẩu.")
                        .build();
            }

            // Generate JWT token containing all required enterprise claims
            String token = jwtTokenProvider.generateToken(user);

            return LoginResponse.builder()
                        .success(true)
                        .message("Đăng nhập thành công.")
                        .token(token)
                        .build();
        } catch (Exception e) {
            log.error("Authentication process error: {}", e.getMessage(), e);
            String message = e instanceof AuthenticationException ? e.getMessage() : "Lỗi kết nối hoặc xử lý thông tin BEAdmin.";
            return LoginResponse.builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}