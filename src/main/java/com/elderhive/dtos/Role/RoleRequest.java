package com.elderhive.dtos.Role;

import com.elderhive.enums.RoleStatusEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private RoleStatusEnum status;
}
