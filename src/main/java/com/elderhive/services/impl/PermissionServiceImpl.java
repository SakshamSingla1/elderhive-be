package com.elderhive.services.impl;

import com.elderhive.dtos.Permission.PermissionResponse;
import com.elderhive.entities.Permission;
import com.elderhive.entities.Role;
import com.elderhive.entities.RolePermission;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.PermissionRepository;
import com.elderhive.repositories.RolePermissionRepository;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.services.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    public List<PermissionResponse> getAll() {
        return permissionRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public String assignToRole(Long roleId, Long permissionId) throws GenericException {
        Role role = findRoleOrThrow(roleId);
        Permission permission = findPermissionOrThrow(permissionId);

        if (rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), permission.getId())) {
            throw new GenericException(ExceptionCodeEnum.CONFLICT,
                    "This permission is already assigned to this role.");
        }

        rolePermissionRepository.save(RolePermission.builder()
                .roleId(role.getId())
                .permissionId(permission.getId())
                .build());

        return "Permission assigned to role successfully.";
    }

    @Override
    @Transactional
    public String unassignFromRole(Long roleId, Long permissionId) throws GenericException {
        Role role = findRoleOrThrow(roleId);
        Permission permission = findPermissionOrThrow(permissionId);

        RolePermission rolePermission = rolePermissionRepository.findByRoleId(role.getId()).stream()
                .filter(rp -> rp.getPermissionId().equals(permission.getId()))
                .findFirst()
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.DATA_NOT_FOUND,
                        "This permission is not currently assigned to this role."));

        rolePermissionRepository.delete(rolePermission);

        return "Permission unassigned from role successfully.";
    }

    private Role findRoleOrThrow(Long roleId) throws GenericException {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.DATA_NOT_FOUND, "Role not found."));
    }

    private Permission findPermissionOrThrow(Long permissionId) throws GenericException {
        return permissionRepository.findById(permissionId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.DATA_NOT_FOUND, "Permission not found."));
    }

    private PermissionResponse toResponse(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .description(permission.getDescription())
                .build();
    }
}
