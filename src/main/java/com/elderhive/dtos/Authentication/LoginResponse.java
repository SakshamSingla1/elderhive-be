package com.elderhive.dtos.Authentication;

import com.elderhive.enums.UserTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The actual HTTP response body for POST /auth/login — deliberately no tokens here;
 *  both are set as httpOnly cookies instead (see AuthController#setAuthCookies). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private Long id;
    private String email;
    private UserTypeEnum userType;
    private List<UserTypeEnum> roles;
}
