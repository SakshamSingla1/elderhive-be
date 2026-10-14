package com.elderhive.dtos.Authentication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Service-layer result only, same reasoning as LoginResult — the controller uses these
 * to set cookies and never puts them in the /auth/refresh response body, which stays
 * an empty success (matching ElderSphere's ApiResponse.successResponse() with no data).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResult {

    private String accessToken;
    private String refreshToken;
}
