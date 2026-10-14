package com.elderhive.dtos.Role;

import com.elderhive.enums.RoleStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private Long id;
    private String name;
    private String description;
    private RoleStatusEnum status;

    // Current Permission.code set granted to this role (via RolePermission) - read-only
    // here; assigning/unassigning is PermissionController's job (Prompt 26), not this one.
    private List<String> permissionCodes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
