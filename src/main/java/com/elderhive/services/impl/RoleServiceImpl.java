package com.elderhive.services.impl;

import com.elderhive.dtos.Role.RoleRequest;
import com.elderhive.dtos.Role.RoleResponse;
import com.elderhive.entities.Permission;
import com.elderhive.entities.Role;
import com.elderhive.entities.RolePermission;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.RoleStatusEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.PermissionRepository;
import com.elderhive.repositories.RolePermissionRepository;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.services.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional
    public RoleResponse create(RoleRequest request) throws GenericException {
        if (roleRepository.existsByName(request.getName())) {
            throw new GenericException(ExceptionCodeEnum.CONFLICT, "A role with this name already exists.");
        }

        Role role = Role.builder()
                .name(request.getName())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : RoleStatusEnum.ACTIVE)
                .build();
        return toResponse(roleRepository.save(role));
    }

    @Override
    @Transactional
    public RoleResponse update(Long id, RoleRequest request) throws GenericException {
        Role role = findOrThrow(id);

        if (!role.getName().equals(request.getName()) && roleRepository.existsByName(request.getName())) {
            throw new GenericException(ExceptionCodeEnum.CONFLICT, "A role with this name already exists.");
        }

        role.setName(request.getName());
        role.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            role.setStatus(request.getStatus());
        }
        return toResponse(roleRepository.save(role));
    }

    @Override
    public RoleResponse getById(Long id) throws GenericException {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<RoleResponse> getAll(Pageable pageable) {
        return roleRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long id) throws GenericException {
        Role role = findOrThrow(id);
        roleRepository.delete(role);
    }

    private Role findOrThrow(Long id) throws GenericException {
        return roleRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.DATA_NOT_FOUND, "Role not found."));
    }

    private RoleResponse toResponse(Role role) {
        List<String> permissionCodes = rolePermissionRepository.findByRoleId(role.getId()).stream()
                .map(RolePermission::getPermissionId)
                .map(permissionId -> permissionRepository.findById(permissionId).map(Permission::getCode).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .status(role.getStatus())
                .permissionCodes(permissionCodes)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
