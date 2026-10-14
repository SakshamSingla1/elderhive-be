package com.elderhive.dtos.NavLink;

import com.elderhive.enums.NavLinkStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavLinkResponse {

    private Long id;
    private String navGroup;
    private Integer navIndex;
    private String name;
    private String path;
    private String icon;
    private String requiredPermission;
    private Boolean superAdminOnly;
    private NavLinkStatusEnum status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
