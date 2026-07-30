package com.agribank.auth_service.client;

import com.agribank.auth_service.dto.request.LoginRequest;
import com.agribank.auth_service.dto.response.UserInfo;
import com.agribank.auth_service.util.PGPEncryptionUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RestBEAdminClientTest {

    private RestTemplate restTemplate;
    private Gson gson;
    private PGPEncryptionUtils pgpEncryptionUtil;
    private RestBEAdminClient restBEAdminClient;

    @BeforeEach
    public void setUp() throws Exception {
        restTemplate = Mockito.mock(RestTemplate.class);
        gson = new Gson();
        
        // Load the actual public key from resources to test real encryption flow
        Resource publicKeyResource = new ClassPathResource("publickey.asc");
        pgpEncryptionUtil = new PGPEncryptionUtils(publicKeyResource);

        restBEAdminClient = new RestBEAdminClient(
                restTemplate,
                gson,
                pgpEncryptionUtil,
                "http://10.0.7.41:8302/agribank/gateway/api/systems/centeralize-login",
                "FEX",
                "FEX",
                "BEADMIN",
                "BILL",
                "10509999"
        );
    }

    @Test
    public void testAuthenticate_Success() throws Exception {
        // 1. Arrange
        LoginRequest loginRequest = new LoginRequest("NAMNH", "123456aA@", null);

        // Prepare mock inner response JSON
        JsonObject innerResponseJson = new JsonObject();
        innerResponseJson.addProperty("responseId", "resp-123");
        innerResponseJson.addProperty("success", true);
        innerResponseJson.addProperty("errorCode", "00");
        innerResponseJson.addProperty("errorDesc", "LOGIN SUCCESS");

        JsonObject userJson = new JsonObject();
        userJson.addProperty("userId", "260306000044430971");
        userJson.addProperty("username", "NAMNH");
        userJson.addProperty("fullname", "Nguyễn Hữu Nam");
        userJson.addProperty("branchCode", "99999999");
        userJson.addProperty("depCode", "00");
        userJson.addProperty("email", "namnh@agribank.com.vn");
        innerResponseJson.add("user", userJson);

        JsonObject roleJson = new JsonObject();
        roleJson.addProperty("groupCode", "QTERP");
        roleJson.addProperty("groupName", "Quản trị ERP");
        innerResponseJson.add("listGrantRole", gson.toJsonTree(new JsonObject[]{roleJson}));

        String innerResponseStr = gson.toJson(innerResponseJson);
        String base64InnerResponse = Base64.getEncoder().encodeToString(innerResponseStr.getBytes(StandardCharsets.UTF_8));

        // Outer response envelope
        JsonObject outerResponseJson = new JsonObject();
        outerResponseJson.addProperty("requestId", "req-123");
        outerResponseJson.addProperty("data", base64InnerResponse);

        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(gson.toJson(outerResponseJson));

        // 2. Act
        UserInfo userInfo = restBEAdminClient.authenticate(loginRequest);

        // 3. Assert
        assertNotNull(userInfo);
        assertEquals("NAMNH", userInfo.getUsername());
        assertEquals("Nguyễn Hữu Nam", userInfo.getFullName());
        assertEquals("QTERP", userInfo.getRole());
        assertTrue(userInfo.getPermissions().contains("QTERP"));
        assertEquals("99999999", userInfo.getBranchCode());
        assertEquals("00", userInfo.getDepartmentCode());
        assertEquals("namnh@agribank.com.vn", userInfo.getEmail());

        // Capture request sent to RestTemplate
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(
                eq("http://10.0.7.41:8302/agribank/gateway/api/systems/centeralize-login"),
                entityCaptor.capture(),
                eq(String.class)
        );

        HttpEntity<String> capturedEntity = entityCaptor.getValue();
        assertNotNull(capturedEntity);
        assertNotNull(capturedEntity.getBody());

        // Validate outer request fields
        JsonObject capturedRequest = gson.fromJson(capturedEntity.getBody(), JsonObject.class);
        assertTrue(capturedRequest.has("requestId"));
        assertTrue(capturedRequest.has("requestTime"));
        assertEquals("FEX", capturedRequest.get("providerId").getAsString());
        assertEquals("BEADMIN", capturedRequest.get("servicesId").getAsString());
        assertTrue(capturedRequest.has("data"));
    }

    @Test
    public void testAuthenticate_FailedCredentials() throws Exception {
        // 1. Arrange
        LoginRequest loginRequest = new LoginRequest("NAMNH", "wrongpassword", null);

        // Prepare mock inner response JSON indicating failure
        JsonObject innerResponseJson = new JsonObject();
        innerResponseJson.addProperty("success", false);
        innerResponseJson.addProperty("errorCode", "01");
        innerResponseJson.addProperty("errorDesc", "LOGIN FAIL");

        String innerResponseStr = gson.toJson(innerResponseJson);
        String base64InnerResponse = Base64.getEncoder().encodeToString(innerResponseStr.getBytes(StandardCharsets.UTF_8));

        // Outer response envelope
        JsonObject outerResponseJson = new JsonObject();
        outerResponseJson.addProperty("data", base64InnerResponse);

        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(gson.toJson(outerResponseJson));

        // 2. Act
        UserInfo userInfo = restBEAdminClient.authenticate(loginRequest);

        // 3. Assert
        assertNull(userInfo);
    }
}
