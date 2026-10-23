package com.elderhive.services.impl;

import com.elderhive.dtos.User.AdminUserResponse;
import com.elderhive.entities.User;
import com.elderhive.entities.UserRoleMapping;
import com.elderhive.enums.ActionType;
import com.elderhive.enums.EntityTypeEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.UserRepository;
import com.elderhive.repositories.UserRoleMappingRepository;
import com.elderhive.services.AdminUserService;
import com.elderhive.services.AuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    // Granting/revoking one of these two specifically requires the caller to already be
    // SUPER_ADMIN - matches ElderSphere's UserRoleServiceImpl#ADMIN_TIER exactly (this
    // prompt's own explicit ask). Granting/revoking ELDER/CARETAKER/FAMILY_MEMBER only
    // needs the controller's own USER_MANAGE gate, which both Admin and Super Admin hold.
    private static final Set<UserTypeEnum> ADMIN_TIER = EnumSet.of(UserTypeEnum.ADMIN, UserTypeEnum.SUPER_ADMIN);

    private final UserRepository userRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Override
    public Page<AdminUserResponse> listUsers(String search, UserTypeEnum userType, UserStatusEnum status, Pageable pageable) {
        String pattern = (search != null && !search.isBlank()) ? "%" + search.trim().toLowerCase() + "%" : null;
        Page<User> page = userRepository.findByCriteria(pattern, userType, status, pageable);

        Map<Long, List<UserTypeEnum>> rolesByUserId = userRoleMappingRepository
                .findByUserIdIn(page.getContent().stream().map(User::getId).toList()).stream()
                .collect(Collectors.groupingBy(UserRoleMapping::getUserId,
                        Collectors.mapping(UserRoleMapping::getUserType, Collectors.toList())));

        return page.map(user -> toResponse(user, rolesByUserId.getOrDefault(user.getId(), List.of())));
    }

    @Override
    public AdminUserResponse getUser(Long id) throws GenericException {
        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public AdminUserResponse suspendUser(Long id) throws GenericException {
        User user = findOrThrow(id);
        user.setStatus(UserStatusEnum.SUSPENDED);
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public AdminUserResponse grantRole(Long targetUserId, UserTypeEnum roleType, User caller) throws GenericException {
        User target = findOrThrow(targetUserId);
        if (ADMIN_TIER.contains(roleType)) {
            requireSuperAdmin(caller);
        }
        if (userRoleMappingRepository.existsByUserIdAndUserType(targetUserId, roleType)) {
            throw new GenericException(ExceptionCodeEnum.USER_ROLE_ALREADY_ASSIGNED,
                    "User already holds the " + roleType + " role");
        }

        userRoleMappingRepository.save(UserRoleMapping.builder()
                .userId(targetUserId)
                .userType(roleType)
                .isPrimary(false)
                .grantedByUserId(caller.getId())
                .build());

        auditLogService.record(EntityTypeEnum.USER, targetUserId.toString(), ActionType.GRANT, caller.getId(),
                toJson(Map.of("roleType", roleType)));

        return toResponse(target);
    }

    @Override
    @Transactional
    public AdminUserResponse revokeRole(Long targetUserId, UserTypeEnum roleType, User caller) throws GenericException {
        User target = findOrThrow(targetUserId);
        if (ADMIN_TIER.contains(roleType)) {
            requireSuperAdmin(caller);
        }

        UserRoleMapping mapping = userRoleMappingRepository.findByUserIdAndUserType(targetUserId, roleType)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_ROLE_NOT_ASSIGNED,
                        "User does not hold the " + roleType + " role"));
        if (mapping.isPrimary()) {
            throw new GenericException(ExceptionCodeEnum.PRIMARY_USER_ROLE_CANNOT_BE_REVOKED,
                    "Cannot revoke the user's primary role");
        }

        userRoleMappingRepository.delete(mapping);

        auditLogService.record(EntityTypeEnum.USER, targetUserId.toString(), ActionType.REVOKE, caller.getId(),
                toJson(Map.of("roleType", roleType)));

        return toResponse(target);
    }

    private void requireSuperAdmin(User caller) throws GenericException {
        if (caller.getUserType() != UserTypeEnum.SUPER_ADMIN) {
            throw new GenericException(ExceptionCodeEnum.FORBIDDEN,
                    "Only a super-admin can grant or revoke the ADMIN or SUPER_ADMIN role");
        }
    }

    private User findOrThrow(Long id) throws GenericException {
        return userRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found"));
    }

    private AdminUserResponse toResponse(User user) {
        List<UserTypeEnum> roles = userRoleMappingRepository.findByUserId(user.getId()).stream()
                .map(UserRoleMapping::getUserType)
                .distinct()
                .toList();
        return toResponse(user, roles);
    }

    private AdminUserResponse toResponse(User user, List<UserTypeEnum> roles) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .primaryRole(user.getUserType())
                .status(user.getStatus())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String toJson(Map<String, ?> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return String.valueOf(data);
        }
    }
}
