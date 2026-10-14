package com.elderhive.dtos.NavLink;

import com.elderhive.enums.NavLinkStatusEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavLinkRequest {

    private String navGroup;

    private Integer navIndex;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Path is required")
    private String path;

    private String icon;

    private String requiredPermission;

    private Boolean superAdminOnly;

    private NavLinkStatusEnum status;
}
