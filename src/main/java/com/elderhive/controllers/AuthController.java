package com.elderhive.controllers;

import com.elderhive.dtos.Authentication.LoginRequest;
import com.elderhive.dtos.Authentication.LoginResponse;
import com.elderhive.dtos.Authentication.LoginResult;
import com.elderhive.dtos.Authentication.RefreshResult;
import com.elderhive.dtos.Authentication.RegisterRequest;
import com.elderhive.dtos.Authentication.RegisterResponse;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.services.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final AuthService authService;

    @Value("${jwt.access-token.expiration}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token.expiration-days}")
    private long refreshTokenExpirationDays;

    // false in local dev (plain http://localhost), true in every real deployment — see
    // application.yml's app.cookie.secure for why this can't just be hardcoded true the
    // way ElderSphere's AuthController does.
    @Value("${app.cookie.secure}")
    private boolean secureCookies;

    @PostMapping("/register")
    public ResponseEntity<ResponseModel<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request)
            throws GenericException {
        RegisterResponse response = authService.register(request);
        return ApiResponse.createSuccess(response, "Registration successful");
    }

    @PostMapping("/login")
    public ResponseEntity<ResponseModel<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                                HttpServletResponse httpResponse)
            throws GenericException {
        LoginResult result = authService.login(request);
        setAuthCookies(httpResponse, result.getAccessToken(), result.getRefreshToken());

        LoginResponse response = LoginResponse.builder()
                .id(result.getId())
                .email(result.getEmail())
                .userType(result.getUserType())
                .roles(result.getRoles())
                .build();
        return ApiResponse.successResponse(response, "Login successful");
    }

    @PostMapping("/refresh")
    public ResponseEntity<ResponseModel<Void>> refresh(HttpServletRequest request, HttpServletResponse response)
            throws GenericException {
        String rawRefreshToken = extractCookieValue(request, REFRESH_TOKEN_COOKIE_NAME);
        RefreshResult result = authService.refresh(rawRefreshToken);
        setAuthCookies(response, result.getAccessToken(), result.getRefreshToken());
        return ApiResponse.successResponse();
    }

    @PostMapping("/logout")
    public ResponseEntity<ResponseModel<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = extractCookieValue(request, REFRESH_TOKEN_COOKIE_NAME);
        authService.logout(rawRefreshToken);
        clearAuthCookies(response);
        return ApiResponse.successResponse();
    }

    private String extractCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Sets both tokens as httpOnly cookies — never in the response body — matching
     * ElderSphere's frontend auth contract (its services/index.ts relies entirely on
     * withCredentials + httpOnly cookies, never reads a token from JS) so a stolen XSS
     * payload can't exfiltrate a session. Two deliberate deviations from ElderSphere's
     * own AuthController#setAuthCookies: secure is environment-driven, not hardcoded
     * true (which would silently break local dev over plain HTTP), and SameSite=Lax
     * per this prompt's explicit instruction, not Strict.
     */
    private void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, accessToken)
                .httpOnly(true)
                .secure(secureCookies)
                .path("/")
                .maxAge(accessTokenExpirationMs / 1000)
                .sameSite("Lax")
                .build();
        // Scoped to /auth (not just /auth/refresh) so the browser also sends it to
        // /auth/logout — cookie Path matching is exact-prefix, so a cookie scoped to
        // /auth/refresh is NEVER sent on a request to /auth/logout, which would make
        // logout silently unable to revoke it. /auth still excludes it from every
        // non-auth endpoint, keeping most of the narrow-scoping security benefit.
        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secureCookies)
                .path("/auth")
                .maxAge(refreshTokenExpirationDays * 24 * 3600)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookies)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookies)
                .path("/auth")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }
}
