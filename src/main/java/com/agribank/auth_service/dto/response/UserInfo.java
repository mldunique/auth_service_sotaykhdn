package com.agribank.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * UserInfo class contains user profile information returned from BEAdmin
 * and mapped into JWT claims.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfo {

    private String username;

    private String fullName;

    private String role;

    private List<String> permissions;

    private String branchCode;

    private String departmentCode;

    private String email;

}