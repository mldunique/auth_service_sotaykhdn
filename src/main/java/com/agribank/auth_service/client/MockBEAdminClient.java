package com.agribank.auth_service.client;

import com.agribank.auth_service.dto.request.LoginRequest;
import com.agribank.auth_service.dto.response.UserInfo;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MockBEAdminClient implements BEAdminClient {

    private final Map<String, MockUserData> mockUsers = new HashMap<>();

    public MockBEAdminClient() {
        initMockUsers();
    }

    private void initMockUsers() {
        // 1. 99ESA081 - Ban Ngân hàng số - NHS quản trị hệ thống
        mockUsers.put("99ESA081", new MockUserData(
                "99ESA081", "Abc123456$", "Đinh Tiến Lâm", "ESA08",
                List.of("PRODUCT_READ", "PRODUCT_WRITE", "CRITERIA_APPROVE", "USER_MANAGEMENT", "ESA08"),
                "10509999", "1050", "99esa081@agribank.com.vn"
        ));

        // 2. 99ECV081 - Ban Ngân hàng số - Tra cứu nội dung đã publish trên hệ thống
        mockUsers.put("99ECV081", new MockUserData(
                "99ECV081", "Abc123456$", "Bùi Phi Anh", "ECV08",
                List.of("PRODUCT_READ", "ECV08"),
                "10509999", "1050", "99ecv081@agribank.com.vn"
        ));

        // 3. 37ETK081 - Ban Khách hàng doanh nghiệp - Kiểm soát viên
        mockUsers.put("37ETK081", new MockUserData(
                "37ETK081", "Abc123456$", "Lê Văn Tuấn", "ETK08",
                List.of("PRODUCT_READ", "CRITERIA_APPROVE", "PRODUCT_APPROVE", "ETK08"),
                "10500037", "1050", "37etk081@agribank.com.vn"
        ));

        // 4. 37ETK082 - Ban Khách hàng doanh nghiệp - Kiểm soát viên
        mockUsers.put("37ETK082", new MockUserData(
                "37ETK082", "Abc123456$", "Lê Thị Huyền Trang", "ETK08",
                List.of("PRODUCT_READ", "CRITERIA_APPROVE", "PRODUCT_APPROVE", "ETK08"),
                "10500037", "1050", "37etk082@agribank.com.vn"
        ));

        // 5. 37ETN081 - Ban Khách hàng doanh nghiệp - Cán bộ quản lý nội dung
        mockUsers.put("37ETN081", new MockUserData(
                "37ETN081", "Abc123456$", "Phạm Thùy Linh", "ETN08",
                List.of("PRODUCT_READ", "PRODUCT_WRITE", "ETN08"),
                "10500037", "1050", "37etn081@agribank.com.vn"
        ));

        // 6. 37ETN082 - Ban Khách hàng doanh nghiệp - Cán bộ quản lý nội dung
        mockUsers.put("37ETN082", new MockUserData(
                "37ETN082", "Abc123456$", "Nguyễn Hải Long", "ETN08",
                List.of("PRODUCT_READ", "PRODUCT_WRITE", "ETN08"),
                "10500037", "1050", "37etn082@agribank.com.vn"
        ));

        // Legacy test user fallbacks
        mockUsers.put("admin", new MockUserData(
                "admin", "123456", "Nguyễn Văn A (Admin)", "ESA08",
                List.of("PRODUCT_READ", "PRODUCT_WRITE", "CRITERIA_APPROVE", "USER_MANAGEMENT", "ESA08"),
                "10509999", "1050", "admin.khdn@agribank.com.vn"
        ));

        mockUsers.put("user", new MockUserData(
                "user", "123456", "Trần Thị B (User)", "ETN08",
                List.of("PRODUCT_READ", "PRODUCT_WRITE", "ETN08"),
                "10500037", "1050", "user.khdn@agribank.com.vn"
        ));
    }

    @Override
    public UserInfo authenticate(LoginRequest request) {
        if (request == null || request.getUsername() == null) {
            return null;
        }

        String usernameKey = request.getUsername().trim();
        MockUserData userData = mockUsers.get(usernameKey);

        // Fallback case-insensitive match
        if (userData == null) {
            for (Map.Entry<String, MockUserData> entry : mockUsers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(usernameKey)) {
                    userData = entry.getValue();
                    break;
                }
            }
        }

        if (userData != null) {
            String reqPass = request.getPassword();
            if (userData.password.equals(reqPass) || "123456".equals(reqPass) || "Abc123456$".equals(reqPass)) {
                String branchCode = (request.getUnitCode() != null && !request.getUnitCode().isBlank())
                        ? request.getUnitCode()
                        : userData.branchCode;

                return UserInfo.builder()
                        .username(userData.username)
                        .fullName(userData.fullName)
                        .role(userData.role)
                        .permissions(userData.permissions)
                        .branchCode(branchCode)
                        .departmentCode(userData.departmentCode)
                        .email(userData.email)
                        .build();
            }
        }
        return null;
    }

    public Map<String, MockUserData> getMockUsers() {
        return mockUsers;
    }

    public static class MockUserData {
        public String username;
        public String password;
        public String fullName;
        public String role;
        public List<String> permissions;
        public String branchCode;
        public String departmentCode;
        public String email;

        public MockUserData(String username, String password, String fullName, String role,
                            List<String> permissions, String branchCode, String departmentCode, String email) {
            this.username = username;
            this.password = password;
            this.fullName = fullName;
            this.role = role;
            this.permissions = permissions;
            this.branchCode = branchCode;
            this.departmentCode = departmentCode;
            this.email = email;
        }
    }
}