package com.agribank.auth_service.client;

import com.agribank.auth_service.dto.request.LoginRequest;
import com.agribank.auth_service.dto.response.UserInfo;
import com.agribank.auth_service.util.PGPEncryptionUtils;
import com.agribank.auth_service.exception.AuthenticationException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Live BEAdminClient communicating with Agribank BEAdmin via encrypted REST API calls.
 */
@Component
public class RestBEAdminClient implements BEAdminClient {

    private static final Logger log = LoggerFactory.getLogger(RestBEAdminClient.class);

    private final RestTemplate restTemplate;
    private final Gson gson;
    private final PGPEncryptionUtils pgpEncryptionUtil;

    private final String url;
    private final String providerId;
    private final String channelId;
    private final String servicesId;
    private final String applicationId;
    private final String defaultBranch;

    public RestBEAdminClient(
            RestTemplate restTemplate,
            Gson gson,
            PGPEncryptionUtils pgpEncryptionUtil,
            @Value("${app.beadmin.url:http://10.0.7.41:8302/agribank/gateway/api/systems/centeralize-login}") String url,
            @Value("${app.beadmin.provider-id:SOTAY}") String providerId,
            @Value("${app.beadmin.channel-id:SOTAY}") String channelId,
            @Value("${app.beadmin.services-id:SOTAY}") String servicesId,
            @Value("${app.beadmin.application-id:BEADMIN}") String applicationId,
            @Value("${app.beadmin.default-branch:10509999}") String defaultBranch) {
        this.restTemplate = restTemplate;
        this.gson = gson;
        this.pgpEncryptionUtil = pgpEncryptionUtil;
        this.url = url;
        this.providerId = providerId;
        this.channelId = channelId;
        this.servicesId = servicesId;
        this.applicationId = applicationId;
        this.defaultBranch = defaultBranch;
    }

    @Override
    public UserInfo authenticate(LoginRequest request) {
        try {
            log.info("Sending authenticate request to BEAdmin for user: {}", request.getUsername());

            String requestId = UUID.randomUUID().toString();
            String requestTime = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());

            // 1. Build plaintext inner request
            Map<String, Object> innerRequest = new HashMap<>();
            innerRequest.put("requestId", requestId);
            innerRequest.put("requestTime", requestTime);
            innerRequest.put("providerId", providerId);
            innerRequest.put("channelId", channelId);
            innerRequest.put("merchantId", providerId);
            innerRequest.put("version", "1.0");
            innerRequest.put("language", "");
            innerRequest.put("signature", "");
            innerRequest.put("servicesId", "BEADMIN");
            innerRequest.put("clientIP", getClientIp());
            innerRequest.put("applicationId", applicationId);

            Map<String, String> userMap = new HashMap<>();
            userMap.put("username", request.getUsername());
            userMap.put("password", request.getPassword());
            String branch = (request.getUnitCode() != null && !request.getUnitCode().isBlank()) 
                    ? request.getUnitCode() 
                    : defaultBranch;
            userMap.put("branchCode", branch);
            userMap.put("actionType", "6");
            userMap.put("prePassword", "");
            userMap.put("fullname", "");
            userMap.put("otp", request.getCaptcha() != null ? request.getCaptcha() : "");

            innerRequest.put("user", userMap);

            // 2. Serialize and PGP-encrypt the inner request DTO
            String beAdminReqStr = gson.toJson(innerRequest);
            log.info("Plaintext BEAdmin request JSON: {}", beAdminReqStr);

            String base64ReqData = pgpEncryptionUtil.encrypt(beAdminReqStr);

            // 3. Assemble the outer wrapper request DTO
            Map<String, Object> outerRequest = new HashMap<>();
            outerRequest.put("requestId", requestId);
            outerRequest.put("requestTime", requestTime);
            outerRequest.put("providerId", providerId);
            outerRequest.put("servicesId", servicesId);
            outerRequest.put("data", base64ReqData);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String outerReqStr = gson.toJson(outerRequest);
            log.info("Encrypted outer request JSON sent to BEAdmin: {}", outerReqStr);

            HttpEntity<String> httpEntity = new HttpEntity<>(outerReqStr, headers);

            // 4. Dispatch the API request to the BEAdmin UAT endpoint
            log.info("Dispatching request to BEAdmin at: {}", url);
            String responseStr = restTemplate.postForObject(url, httpEntity, String.class);
            if (responseStr == null) {
                log.warn("Null response returned from BEAdmin");
                return null;
            }

            log.info("Raw response from BEAdmin: {}", responseStr);

            // 5. Parse outer response and extract "data" containing the inner response string
            JsonObject responseJson = gson.fromJson(responseStr, JsonObject.class);
            if (responseJson == null || !responseJson.has("data")) {
                log.warn("Response from BEAdmin is empty or missing data field");
                return null;
            }

            String dataResBase64 = responseJson.get("data").getAsString();

            // 6. Decode Base64 payload (no PGP decryption on response per reference implementation)
            byte[] dataResDecoded = Base64.getDecoder().decode(dataResBase64);
            String resStr = new String(dataResDecoded, StandardCharsets.UTF_8);
            log.info("Decoded BEAdmin response JSON: {}", resStr);

            // 7. Parse inner response contents
            JsonObject innerResponse = gson.fromJson(resStr, JsonObject.class);
            if (innerResponse == null) {
                log.warn("Decoded inner response could not be parsed as JSON");
                return null;
            }

            // Check response status
            boolean success = innerResponse.has("success") && innerResponse.get("success").getAsBoolean();
            String errorCode = innerResponse.has("errorCode") ? innerResponse.get("errorCode").getAsString() : "";
            String errorDesc = innerResponse.has("errorDesc") ? innerResponse.get("errorDesc").getAsString() : "";

            log.info("BEAdmin response status - success: {}, errorCode: {}, errorDesc: {}", success, errorCode, errorDesc);

            if (!success || !"00".equals(errorCode)) {
                log.warn("BEAdmin authentication rejected: {} - {}", errorCode, errorDesc);
                if ("01".equals(errorCode) || (errorDesc != null && errorDesc.toUpperCase().contains("OTP"))) {
                    throw new AuthenticationException("invalid OTP");
                } else {
                    throw new AuthenticationException("Sai tài khoản hoặc mật khẩu.");
                }
            }

            JsonObject userJson = innerResponse.getAsJsonObject("user");
            if (userJson == null) {
                log.warn("BEAdmin login successful, but user object is missing in response");
                return null;
            }

            // 8. Map to the internal application UserInfo DTO
            String username = userJson.has("username") ? userJson.get("username").getAsString() : request.getUsername();
            String fullname = userJson.has("fullname") ? userJson.get("fullname").getAsString() : "";
            String branchCode = userJson.has("branchCode") ? userJson.get("branchCode").getAsString() : defaultBranch;
            String depCode = userJson.has("depCode") ? userJson.get("depCode").getAsString() : "";
            String email = userJson.has("email") ? userJson.get("email").getAsString() : "";

            // Map roles & permissions from userJson or innerResponse
            String role = "USER";
            List<String> permissions = new ArrayList<>();

            JsonArray rolesArray = null;
            if (userJson.has("listGrantRole") && !userJson.get("listGrantRole").isJsonNull()) {
                rolesArray = userJson.getAsJsonArray("listGrantRole");
            } else if (innerResponse.has("listGrantRole") && !innerResponse.get("listGrantRole").isJsonNull()) {
                rolesArray = innerResponse.getAsJsonArray("listGrantRole");
            }

            if (rolesArray != null && rolesArray.size() > 0) {
                JsonObject firstRole = rolesArray.get(0).getAsJsonObject();
                if (firstRole.has("groupCode")) {
                    role = firstRole.get("groupCode").getAsString();
                }
                for (JsonElement roleElement : rolesArray) {
                    JsonObject roleObj = roleElement.getAsJsonObject();
                    if (roleObj.has("groupCode")) {
                        permissions.add(roleObj.get("groupCode").getAsString());
                    }
                }
            }

            // Fallback: If role is still USER or empty, infer role from username (e.g., 37ETK081 -> ETK08)
            if ("USER".equalsIgnoreCase(role) || role == null || role.isBlank()) {
                String u = username.toUpperCase();
                if (u.contains("ETK08")) {
                    role = "ETK08";
                } else if (u.contains("ETN08")) {
                    role = "ETN08";
                } else if (u.contains("ESA08") || u.contains("ADMIN")) {
                    role = "ESA08";
                }
            }

            // If permissions list is empty, assign some basic default permission based on role mapping
            if (permissions.isEmpty()) {
                permissions.add("PRODUCT_READ");
                if ("ADMIN".equalsIgnoreCase(role) || "QTERP".equalsIgnoreCase(role)) {
                    permissions.addAll(List.of("PRODUCT_WRITE", "CRITERIA_APPROVE", "USER_MANAGEMENT"));
                }
            }

            return UserInfo.builder()
                    .username(username)
                    .fullName(fullname)
                    .role(role)
                    .permissions(permissions)
                    .branchCode(branchCode)
                    .departmentCode(depCode)
                    .email(email)
                    .build();

        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Exception during BEAdmin remote authentication calling: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi kết nối hoặc xử lý thông tin BEAdmin: " + e.getMessage(), e);
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
}
