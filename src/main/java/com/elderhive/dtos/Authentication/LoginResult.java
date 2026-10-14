package com.elderhive.dtos.Authentication;

import com.elderhive.enums.UserTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Service-layer result only — carries the raw access/refresh tokens so the controller
 * can set them as cookies. NEVER serialized directly as an HTTP response body; the
 * controller builds a separate, token-free LoginResponse for that.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {

    private String accessToken;
    private String refreshToken;
    private Long id;
    private String email;
    private UserTypeEnum userType;
    private List<UserTypeEnum> roles;
}
