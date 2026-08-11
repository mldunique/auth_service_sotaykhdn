package com.agribank.auth_service.controller;

import com.agribank.auth_service.model.Branch;
import com.agribank.auth_service.repository.BranchRepository;
import com.agribank.auth_service.util.PGPEncryptionUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchSyncController {

    private static final Logger log = LoggerFactory.getLogger(BranchSyncController.class);

    private final BranchRepository branchRepository;
    private final RestTemplate restTemplate;
    private final Gson gson;
    private final PGPEncryptionUtils pgpEncryptionUtil;

    private final String url;
    private final String providerId;
    private final String channelId;
    private final String servicesId;
    private final String applicationId;
    private final boolean isMock;

    public BranchSyncController(
            BranchRepository branchRepository,
            RestTemplate restTemplate,
            Gson gson,
            PGPEncryptionUtils pgpEncryptionUtil,
            @Value("${app.beadmin.url}") String url,
            @Value("${app.beadmin.provider-id}") String providerId,
            @Value("${app.beadmin.channel-id}") String channelId,
            @Value("${app.beadmin.services-id}") String servicesId,
            @Value("${app.beadmin.application-id}") String applicationId,
            @Value("${app.beadmin.mock:false}") boolean isMock) {
        this.branchRepository = branchRepository;
        this.restTemplate = restTemplate;
        this.gson = gson;
        this.pgpEncryptionUtil = pgpEncryptionUtil;
        this.url = url;
        this.providerId = providerId;
        this.channelId = channelId;
        this.servicesId = servicesId;
        this.applicationId = applicationId;
        this.isMock = isMock;
    }

    @PostMapping("/sync")
    public ResponseEntity<String> syncBranches() {
        try {
            log.info("Sending sync request to BEAdmin...");

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
            innerRequest.put("clientIP", "127.0.0.1");
            innerRequest.put("applicationId", applicationId);

            Map<String, String> userMap = new HashMap<>();
            userMap.put("username", "99ESA081");
            userMap.put("password", "");
            userMap.put("branchCode", "10509999");
            userMap.put("actionType", "5");
            userMap.put("prePassword", "");
            userMap.put("fullname", "");
            userMap.put("otp", "");

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

            HttpEntity<String> httpEntity = new HttpEntity<>(outerReqStr, headers);

            // 4. Dispatch the API request to the BEAdmin UAT endpoint
            String responseStr = restTemplate.postForObject(url, httpEntity, String.class);
            if (responseStr == null) {
                return ResponseEntity.status(500).body("Null response returned from BEAdmin");
            }

            // 5. Parse outer response and extract "data" containing the inner response string
            JsonObject responseJson = gson.fromJson(responseStr, JsonObject.class);
            if (responseJson == null || !responseJson.has("data")) {
                return ResponseEntity.status(500).body("Response from BEAdmin is empty or missing data field");
            }

            String dataResBase64 = responseJson.get("data").getAsString();

            // 6. Decode Base64 payload
            byte[] dataResDecoded = Base64.getDecoder().decode(dataResBase64);
            String resStr = new String(dataResDecoded, StandardCharsets.UTF_8);
            log.info("Decoded BEAdmin response JSON: {}", resStr);

            // 7. Parse inner response contents
            JsonObject innerResponse = gson.fromJson(resStr, JsonObject.class);
            if (innerResponse == null) {
                return ResponseEntity.status(500).body("Decoded inner response could not be parsed as JSON");
            }

            JsonArray branchesArray = null;
//            if (innerResponse.has("listBranch")) {
//                branchesArray = innerResponse.getAsJsonArray("listBranch");
//            } else if (innerResponse.has("listUnit")) {
//                branchesArray = innerResponse.getAsJsonArray("listUnit");
//            } else if (innerResponse.has("branches")) {
//                branchesArray = innerResponse.getAsJsonArray("branches");
//            } else {
//                // Find any JSON array inside the root object
//                for (Map.Entry<String, JsonElement> entry : innerResponse.entrySet()) {
//                    if (entry.getValue().isJsonArray()) {
//                        branchesArray = entry.getValue().getAsJsonArray();
//                        break;
//                    }
//                }
//            }
//
//            if (branchesArray == null) {
//                return ResponseEntity.ok("Successfully decoded but no branch list found in response. Response content: " + resStr);
//            }

            if (innerResponse.has("listBank")) {
                branchesArray = innerResponse.getAsJsonArray("listBank");
            } else {
                // Find any JSON array inside the root object
                for (Map.Entry<String, JsonElement> entry : innerResponse.entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        branchesArray = entry.getValue().getAsJsonArray();
                        break;
                    }
                }
            }

            if (branchesArray == null) {
                return ResponseEntity.ok("Successfully decoded but no branch list found in response. Response content: " + resStr);
            }

            int count = 0;
            for (JsonElement el : branchesArray) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String code = null;
                String name = null;
                
                if (obj.has("branchCode")) code = obj.get("branchCode").getAsString();
                
                if (obj.has("branchName")) name = obj.get("branchName").getAsString();

                if (code != null && !code.isBlank() && name != null && !name.isBlank()) {
                    Optional<Branch> opt = branchRepository.findByBranchCode(code);
                    Branch branch;
                    if (opt.isPresent()) {
                        branch = opt.get();
                        branch.setBranchName(name);
                    } else {
                        branch = Branch.builder()
                                .branchCode(code)
                                .branchName(name)
                                .build();
                    }
                    branchRepository.save(branch);
                    count++;
                }
            }

            return ResponseEntity.ok("Successfully synced " + count + " branches! Raw response was: " + resStr);

        } catch (Exception e) {
            log.error("Exception during BEAdmin branch sync: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error syncing branches: " + e.getMessage());
        }
    }

    @GetMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookupBranch(@RequestParam("code") String code) {
        Optional<Branch> opt = branchRepository.findByBranchCode(code);
        Map<String, Object> response = new HashMap<>();
        if (opt.isPresent()) {
            response.put("success", true);
            response.put("name", opt.get().getBranchName());
        } else {
            response.put("success", false);
            response.put("name", "");
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users-from-beadmin")
    public ResponseEntity<String> getUsersFromBEAdmin(
            @RequestParam(name = "username", required = false, defaultValue = "") String currentUsername,
            @RequestParam(name = "branchCode", required = false, defaultValue = "") String currentBranchCode) {
        try {
            log.info("Sending users query request to BEAdmin (actionType=4) for user: {}, branch: {}...", currentUsername, currentBranchCode);

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
            innerRequest.put("clientIP", "127.0.0.1");
            innerRequest.put("applicationId", applicationId);

            Map<String, String> userMap = new HashMap<>();
            userMap.put("username", currentUsername);
            userMap.put("password", "");
            userMap.put("branchCode", currentBranchCode);
            userMap.put("actionType", "4");
            userMap.put("prePassword", "");
            userMap.put("fullname", "");
            userMap.put("otp", "");

            innerRequest.put("user", userMap);

            // 2. Serialize and PGP-encrypt the inner request
            String beAdminReqStr = gson.toJson(innerRequest);
            String base64ReqData = pgpEncryptionUtil.encrypt(beAdminReqStr);

            // 3. Assemble outer wrapper request
            Map<String, Object> outerRequest = new HashMap<>();
            outerRequest.put("requestId", requestId);
            outerRequest.put("requestTime", requestTime);
            outerRequest.put("providerId", providerId);
            outerRequest.put("servicesId", servicesId);
            outerRequest.put("data", base64ReqData);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String outerReqStr = gson.toJson(outerRequest);

            HttpEntity<String> httpEntity = new HttpEntity<>(outerReqStr, headers);

            // 4. Dispatch the API request
            String responseStr = restTemplate.postForObject(url, httpEntity, String.class);
            if (responseStr == null) {
                return ResponseEntity.status(500).body("Null response returned from BEAdmin");
            }

            // 5. Parse outer response
            JsonObject responseJson = gson.fromJson(responseStr, JsonObject.class);
            if (responseJson == null || !responseJson.has("data")) {
                return ResponseEntity.status(500).body("Response from BEAdmin is empty or missing data field");
            }

            String dataResBase64 = responseJson.get("data").getAsString();

            // 6. Decode Base64 payload
            byte[] dataResDecoded = Base64.getDecoder().decode(dataResBase64);
            String resStr = new String(dataResDecoded, StandardCharsets.UTF_8);
            log.info("Decoded BEAdmin users response JSON: {}", resStr);

            // 7. Extract only branchCode, fullname, and username from the user list, filtered by allowed roles (ESA08, ECV08, ETK08, ETN08)
            JsonObject innerResponse = gson.fromJson(resStr, JsonObject.class);
            List<Map<String, String>> cleanUsers = new ArrayList<>();
            if (innerResponse != null && innerResponse.has("listUser")) {
                JsonArray listUser = innerResponse.getAsJsonArray("listUser");
                for (JsonElement element : listUser) {
                    if (element.isJsonObject()) {
                        JsonObject u = element.getAsJsonObject();

                        // Check role list for allowed roles
                        boolean hasAllowedRole = false;
                        String matchedGroupCode = "";
                        if (u.has("listGrantRoleOfUser")) {
                            JsonArray roles = u.getAsJsonArray("listGrantRoleOfUser");
                            for (JsonElement roleEl : roles) {
                                if (roleEl.isJsonObject()) {
                                    JsonObject r = roleEl.getAsJsonObject();
                                    if (r.has("groupCode")) {
                                        String gCode = r.get("groupCode").getAsString();
                                        if ("ESA08".equals(gCode) || "ECV08".equals(gCode) || "ETK08".equals(gCode) || "ETN08".equals(gCode)) {
                                            hasAllowedRole = true;
                                            matchedGroupCode = gCode;
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        if (hasAllowedRole) {
                            Map<String, String> cleanUser = new HashMap<>();
                            cleanUser.put("branchCode", u.has("branchCode") ? u.get("branchCode").getAsString() : "");
                            cleanUser.put("fullname", u.has("fullname") ? u.get("fullname").getAsString() : "");
                            cleanUser.put("username", u.has("username") ? u.get("username").getAsString() : "");
                            cleanUser.put("groupCode", matchedGroupCode);
                            cleanUsers.add(cleanUser);
                        }
                    }
                }
            }

            return ResponseEntity.ok(gson.toJson(cleanUsers));

        } catch (Exception e) {
            log.error("Exception during BEAdmin users query: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error querying users: " + e.getMessage());
        }
    }

    @PostMapping("/google-authenticator")
    public ResponseEntity<Map<String, Object>> getGoogleAuthenticator(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam(name = "branchCode", required = false) String branchCode) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            log.info("Requesting Google Authenticator QR Code from BEAdmin for user: {} (isMock: {})...", username, isMock);

            if (isMock) {
                // Mock response for Google Authenticator (actionType=7)
                response.put("success", true);
                response.put("decodedData", "{\"success\":true,\"user\":{\"username\":\"" + username + "\",\"qrCode\":\"iVBORw0KGgoAAAANSUhEUgAAASwAAAEsAQAAAABRBrPYAAAC/ElEQVR42u2aPa6jUAyFjSgoWQI7STYWCSQ2xtvJXQIlRYTnnOPwNCEppptrCYooge8Wlv+OTcz/5Vrtwi7swi7swv4Pthku/Bh9a/w52M0e5j8+F9xuk2B397JZDyKu3kv3s44Fz+Yk2M3a4ks/64B37k+zBhiOZsI22ic/4efosLTNhvkCL23Nis/Gp+E4mgVTvDX0006Pwdyh+8G9b2FZKcY02W79XE4fX4pDpVhccNbAcgXD14d1vrZfS3SdGCxtC8uV+34ULjyjx1CPs2BI84VRtuMDd56weY0DSTDlupFYbCx2R+3tln6yk6U1YzQIaT7RO8aEf7Knm6pXDgxRNkYHZLwNKlzogA96bEyC0SqUXfVv+Q79+74qBpNg1IAGj00GS9H3QoygiauOpcDkE5pWfIcklJTqIt76JBi1E+MNDxVvL0FrIapyYJEe/pIgpocoXMOp11eMMeEpPHifRoazcIqqNgfGJs6Eh4y9R+t+RLz53s85MFyTGVuFMfX1LZp4815768U0PcBIYxNvlUGMwdZPY0XFmGuQayJ59C3iLXpIDkz2WZRdzdmTgaDNb5ZWjOm+6dYhY9n8prOgrRjzPSYKaXK0D5xiKXbqkDYJhinI/NdtynVo8tg55cCU8GyDprKLlFG8ccp492nNGDOcGxoJc014GjDsvKupFtNAiopr0TlC3yLrPxcd9WKMLdN6Q0YuR9Zv742yZowSxBp4jClT5Ls4dcr6ejHtl7QqwIQ3sXqNTH37zKxqMdXeTgtwRBht5o7jWB/kwKg5kC0g2AZNJSza+Sne6sWW0OTza0EQWY+jsj4FpiKlzV+ojzA3dEgSzI/lZelCk8fiqf2Mt2qx3x2+612iPKZdDdw2J8H4JiV2+C9Nrm7yYWnNmN5d/aXJ0Qalp+y80qkcM2nyXqsC7s5g+GlvWT3Gl+ucLZjrz0E7fGvck2B6q/h6o0t1HiP2NNjt808ClWLKekopBJ0XVVwtOvTfhxTY9b+aC7uwC7uwxNgfduzqM1BmO+MAAAAASUVORK5CYII=\"}}}");
                return ResponseEntity.ok(response);
            }

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
            innerRequest.put("clientIP", "127.0.0.1");
            innerRequest.put("applicationId", applicationId);

            Map<String, String> userMap = new HashMap<>();
            userMap.put("username", username);
            userMap.put("password", password);
            String branch = (branchCode != null && !branchCode.isBlank()) ? branchCode : "10509999";
            userMap.put("branchCode", branch);
            userMap.put("actionType", "7");
            userMap.put("prePassword", "");
            userMap.put("fullname", "");
            userMap.put("otp", "");

            innerRequest.put("user", userMap);

            // 2. Serialize and PGP-encrypt the inner request
            String beAdminReqStr = gson.toJson(innerRequest);
            String base64ReqData = pgpEncryptionUtil.encrypt(beAdminReqStr);

            // 3. Assemble outer wrapper request
            Map<String, Object> outerRequest = new HashMap<>();
            outerRequest.put("requestId", requestId);
            outerRequest.put("requestTime", requestTime);
            outerRequest.put("providerId", providerId);
            outerRequest.put("servicesId", servicesId);
            outerRequest.put("data", base64ReqData);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String outerReqStr = gson.toJson(outerRequest);

            HttpEntity<String> httpEntity = new HttpEntity<>(outerReqStr, headers);

            // 4. Dispatch the API request to the BEAdmin UAT endpoint
            String responseStr = restTemplate.postForObject(url, httpEntity, String.class);
            if (responseStr == null) {
                response.put("success", false);
                response.put("message", "Null response returned from BEAdmin");
                return ResponseEntity.status(500).body(response);
            }

            // 5. Parse outer response
            JsonObject responseJson = gson.fromJson(responseStr, JsonObject.class);
            if (responseJson == null || !responseJson.has("data")) {
                response.put("success", false);
                response.put("message", "Response from BEAdmin is empty or missing data field");
                return ResponseEntity.status(500).body(response);
            }

            String dataResBase64 = responseJson.get("data").getAsString();

            // 6. Decode Base64 payload
            byte[] dataResDecoded = Base64.getDecoder().decode(dataResBase64);
            String resStr = new String(dataResDecoded, StandardCharsets.UTF_8);
            log.info("Decoded BEAdmin Google Authenticator response: {}", resStr);

            response.put("success", true);
            response.put("decodedData", resStr);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error requesting Google Authenticator from BEAdmin", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
