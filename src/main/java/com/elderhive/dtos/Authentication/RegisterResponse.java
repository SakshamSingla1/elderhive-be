package com.elderhive.dtos.Authentication;

import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {

    private Long id;
    private String email;
    private String fullName;
    private UserTypeEnum userType;
    private UserStatusEnum status;
}
