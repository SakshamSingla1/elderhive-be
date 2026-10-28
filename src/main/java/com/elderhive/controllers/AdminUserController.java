package com.elderhive.controllers;

import com.elderhive.dtos.User.AdminUserResponse;
import com.elderhive.dtos.User.AssignFineGrainedRoleRequest;
import com.elderhive.dtos.User.GrantUserRoleRequest;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 93's admin user-management console: list/search/suspend users, plus multi-role
 * grant/revoke (UserRoleMapping rows). Every endpoint is gated by USER_MANAGE (which both
 * Admin and Super Admin hold); granting/revoking ADMIN or SUPER_ADMIN specifically is
 * additionally restricted to SUPER_ADMIN callers inside AdminUserServiceImpl itself, not
 * here - matching ~/ElderSphere/eldersphere-be's UserRoleServiceImpl#requireSuperAdmin.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserRepository userRepository;

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('USER_MANAGE')")
    @GetMapping
    public ResponseEntity<ResponseModel<Page<AdminUserResponse>>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserTypeEnum userType,
            @RequestParam(required = false) UserStatusEnum status,
            Pageable pageable) {
        return ApiResponse.successResponse(adminUserService.listUsers(search, userType, status, pageable), "Users fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('USER_MANAGE')")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseModel<AdminUserResponse>> getUser(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(adminUserService.getUser(id), "User fetched successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('USER_MANAGE')")
    @PutMapping("/{id}/suspend")
    public ResponseEntity<ResponseModel<AdminUserResponse>> suspendUser(@PathVariable Long id) throws GenericException {
        return ApiResponse.successResponse(adminUserService.suspendUser(id), "User suspended successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('USER_MANAGE')")
    @PostMapping("/{id}/roles")
    public ResponseEntity<ResponseModel<AdminUserResponse>> grantRole(
            @PathVariable Long id, @Valid @RequestBody GrantUserRoleRequest request) throws GenericException {
        return ApiResponse.successResponse(adminUserService.grantRole(id, request.getRoleType(), currentUser()), "Role granted successfully");
    }

    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('USER_MANAGE')")
    @DeleteMapping("/{id}/roles/{roleType}")
    public ResponseEntity<ResponseModel<AdminUserResponse>> revokeRole(
            @PathVariable Long id, @PathVariable UserTypeEnum roleType) throws GenericException {
        return ApiResponse.successResponse(adminUserService.revokeRole(id, roleType, currentUser()), "Role revoked successfully");
    }

    /**
     * Sets (or clears, with a null roleId) the fine-grained Role an ADMIN account holds.
     * Gated by ROLE_MANAGE rather than USER_MANAGE - same reasoning as RoleController's own
     * endpoints (ROLE_MANAGE is deliberately kept out of the curated "Admin" role, so this
     * is Super-Admin-only in practice despite not being a bare hasRole('SUPER_ADMIN')
     * check). Without this endpoint, AdminBootstrapSeeder's ADMIN_BOOTSTRAP_EMAIL account
     * is the only ADMIN that can ever hold a fine-grained role.
     */
    @PreAuthorize("hasRole('ADMIN') and @adminPermissionGuard.has('ROLE_MANAGE')")
    @PutMapping("/{id}/role")
    public ResponseEntity<ResponseModel<AdminUserResponse>> assignFineGrainedRole(
            @PathVariable Long id, @RequestBody AssignFineGrainedRoleRequest request) throws GenericException {
        return ApiResponse.successResponse(
                adminUserService.assignFineGrainedRole(id, request.getRoleId(), currentUser()), "Role assigned successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
