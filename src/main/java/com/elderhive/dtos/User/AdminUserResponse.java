package com.elderhive.dtos.User;

import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Backs AdminUserController's list/search/get/suspend/grant/revoke endpoints (Prompt 93).
 * {@code primaryRole} mirrors {@code User.userType}; {@code roles} is every UserTypeEnum
 * this user currently holds via UserRoleMapping (always includes primaryRole). {@code
 * roleId} mirrors {@code User.roleId} - only ever meaningful for an ADMIN (see
 * AdminPermissionGuard); null means "no fine-grained role assigned yet" (fail-closed).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private UserTypeEnum primaryRole;
    private UserStatusEnum status;
    private List<UserTypeEnum> roles;
    private Long roleId;
    private LocalDateTime createdAt;
}
