package com.elderhive.dtos.User;

import com.elderhive.enums.UserTypeEnum;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrantUserRoleRequest {

    @NotNull
    private UserTypeEnum roleType;
}
