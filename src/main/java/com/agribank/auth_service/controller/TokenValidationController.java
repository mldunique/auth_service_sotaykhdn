package com.agribank.auth_service.controller;

import com.agribank.auth_service.dto.response.UserInfo;
import com.agribank.auth_service.exception.AuthenticationException;
import com.agribank.auth_service.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller providing token validation endpoints for downstream microservices / WebApps.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class TokenValidationController {

    private final JwtTokenProvider jwtTokenProvider;

    public TokenValidationController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Validates Bearer JWT token and returns user profile claims.
     */
    @PostMapping("/validate")
    public ResponseEntity<UserInfo> validateToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new AuthenticationException("Thiếu token xác thực hoặc format không hợp lệ");
        }

        String token = authHeader.substring(7);

        if (!jwtTokenProvider.validateToken(token)) {
            throw new AuthenticationException("Token không hợp lệ hoặc đã hết hạn");
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        Claims claims = jwtTokenProvider.getClaimsFromToken(token);

        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);

        UserInfo userInfo = UserInfo.builder()
                .username(username)
                .fullName(claims.get("fullName", String.class))
                .role(claims.get("role", String.class))
                .permissions(permissions)
                .branchCode(claims.get("branchCode", String.class))
                .departmentCode(claims.get("departmentCode", String.class))
                .email(claims.get("email", String.class))
                .build();

        return ResponseEntity.ok(userInfo);
    }
}
