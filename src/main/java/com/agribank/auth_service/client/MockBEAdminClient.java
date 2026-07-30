package com.agribank.auth_service.client;

import com.agribank.auth_service.dto.request.LoginRequest;
import com.agribank.auth_service.dto.response.UserInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.beadmin.mock", havingValue = "true", matchIfMissing = true)
public class MockBEAdminClient implements BEAdminClient {

    @Override
    public UserInfo authenticate(LoginRequest request) {
        if ("admin".equals(request.getUsername()) && "123456".equals(request.getPassword())) {
            return UserInfo.builder()
                    .username("admin")
                    .fullName("Nguyễn Văn A")
                    .role("ADMIN")
                    .permissions(List.of("PRODUCT_READ", "PRODUCT_WRITE", "CRITERIA_APPROVE", "USER_MANAGEMENT"))
                    .branchCode("001")
                    .departmentCode("KHDN")
                    .email("admin.khdn@agribank.com.vn")
                    .build();
        } else if ("user".equals(request.getUsername()) && "123456".equals(request.getPassword())) {
            return UserInfo.builder()
                    .username("user")
                    .fullName("Trần Thị B")
                    .role("USER")
                    .permissions(List.of("PRODUCT_READ"))
                    .branchCode("002")
                    .departmentCode("KHDN")
                    .email("user.khdn@agribank.com.vn")
                    .build();
        }
        return null;
    }
}