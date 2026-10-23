package com.elderhive.services;

import com.elderhive.dtos.User.AdminUserResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Prompt 93's admin user-management console: list/search, suspend, and multi-role
 * grant/revoke (UserRoleMapping rows), every grant/revoke audited via AuditLogService.
 * USER_MANAGE-gated at the controller; granting/revoking ADMIN or SUPER_ADMIN specifically
 * additionally requires the caller to themselves be SUPER_ADMIN - enforced here, not at
 * the controller, matching ~/ElderSphere/eldersphere-be's own
 * UserRoleServiceImpl#requireSuperAdmin check exactly.
 */
public interface AdminUserService {

    Page<AdminUserResponse> listUsers(String search, UserTypeEnum userType, UserStatusEnum status, Pageable pageable);

    AdminUserResponse getUser(Long id) throws GenericException;

    AdminUserResponse suspendUser(Long id) throws GenericException;

    AdminUserResponse grantRole(Long targetUserId, UserTypeEnum roleType, User caller) throws GenericException;

    AdminUserResponse revokeRole(Long targetUserId, UserTypeEnum roleType, User caller) throws GenericException;
}
