package com.elderhive.services.impl;

import com.elderhive.dtos.Authentication.ForgotPasswordRequest;
import com.elderhive.dtos.Authentication.LoginRequest;
import com.elderhive.dtos.Authentication.LoginResult;
import com.elderhive.dtos.Authentication.RefreshResult;
import com.elderhive.dtos.Authentication.RegisterRequest;
import com.elderhive.dtos.Authentication.RegisterResponse;
import com.elderhive.dtos.Authentication.ResetPasswordRequest;
import com.elderhive.entities.PasswordResetToken;
import com.elderhive.entities.RefreshToken;
import com.elderhive.entities.User;
import com.elderhive.entities.UserRoleMapping;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.PasswordResetTokenRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.repositories.UserRoleMappingRepository;
import com.elderhive.security.JwtUtil;
import com.elderhive.services.AuthService;
import com.elderhive.services.EmailService;
import com.elderhive.services.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    // Self-registration is deliberately restricted — ADMIN/SUPER_ADMIN accounts are
    // admin-API-only (a later phase), never created through this public endpoint.
    private static final Set<UserTypeEnum> SELF_REGISTERABLE_ROLES =
            Set.of(UserTypeEnum.ELDER, UserTypeEnum.CARETAKER, UserTypeEnum.FAMILY_MEMBER);

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    @Value("${app.password-reset.token-expiry-minutes}")
    private int passwordResetExpiryMinutes;

    @Value("${app.password-reset.url}")
    private String passwordResetUrl;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) throws GenericException {
        if (!SELF_REGISTERABLE_ROLES.contains(request.getUserType())) {
            throw new GenericException(ExceptionCodeEnum.INVALID_ROLE,
                    "Self-registration is only allowed for ELDER, CARETAKER, or FAMILY_MEMBER accounts.");
        }

        if (request.getPassword() == null || request.getPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new GenericException(ExceptionCodeEnum.PASSWORD_TOO_WEAK,
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new GenericException(ExceptionCodeEnum.DUPLICATE_EMAIL,
                    "An account with this email already exists.");
        }

        User user = userRepository.save(User.builder()
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .userType(request.getUserType())
                .status(UserStatusEnum.ACTIVE)
                .build());

        // Every user gets a matching primary UserRoleMapping row so multi-role support
        // (granting additional roles later) has a consistent starting point - the same
        // save that creates the User must succeed-or-fail together with this one, hence
        // @Transactional above.
        userRoleMappingRepository.save(UserRoleMapping.builder()
                .userId(user.getId())
                .userType(user.getUserType())
                .isPrimary(true)
                .build());

        return RegisterResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .userType(user.getUserType())
                .status(user.getStatus())
                .build();
    }

    @Override
    public LoginResult login(LoginRequest request) throws GenericException {
        // Same exception (code + message) for "no such email" and "wrong password" —
        // deliberately not distinguishing which one failed, so this endpoint can't be
        // used to enumerate which emails have accounts.
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.INVALID_CREDENTIALS, "Invalid email or password."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new GenericException(ExceptionCodeEnum.INVALID_CREDENTIALS, "Invalid email or password.");
        }

        if (user.getStatus() != UserStatusEnum.ACTIVE) {
            throw new GenericException(ExceptionCodeEnum.ACCOUNT_NOT_ACTIVE,
                    "This account is not active. Contact support for help.");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        String refreshToken = refreshTokenService.issue(user);

        List<UserTypeEnum> roles = userRoleMappingRepository.findByUserId(user.getId()).stream()
                .map(UserRoleMapping::getUserType)
                .toList();

        return LoginResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .id(user.getId())
                .email(user.getEmail())
                .userType(user.getUserType())
                .roles(roles)
                .build();
    }

    @Override
    public RefreshResult refresh(String rawRefreshToken) throws GenericException {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new GenericException(ExceptionCodeEnum.TOKEN_INVALID, "No refresh token provided.");
        }

        // validate() already filters out revoked/expired rows (Prompt 13) - a single
        // generic TOKEN_INVALID for "not found, revoked, or expired" rather than
        // distinguishing which, same not-leaking-why principle as login's INVALID_CREDENTIALS.
        RefreshToken storedToken = refreshTokenService.validate(rawRefreshToken)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.TOKEN_INVALID, "Invalid or expired refresh token."));

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));

        // Rotation: the old token is revoked and a brand new one issued, every time -
        // a stolen-and-replayed refresh token is usable at most once before its real
        // owner's next refresh call invalidates it (standard rotation-theft detection).
        refreshTokenService.revoke(rawRefreshToken);
        String newRefreshToken = refreshTokenService.issue(user);
        String newAccessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());

        return RefreshResult.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Override
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    @Override
    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        // Do not reveal whether the email exists — this branch is the only difference
        // between "account found" and "not found", and it never surfaces to the caller.
        Optional<User> maybeUser = userRepository.findByEmail(request.getEmail());

        if (maybeUser.isPresent()) {
            User user = maybeUser.get();
            // Invalidate any previously-issued, still-unused reset link before creating
            // a new one, so only the most recent link is ever valid.
            passwordResetTokenRepository.deleteByUserId(user.getId());

            String token = UUID.randomUUID().toString();
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .token(token)
                    .userId(user.getId())
                    .expiryDate(LocalDateTime.now().plusMinutes(passwordResetExpiryMinutes))
                    .used(false)
                    .build());

            String resetLink = passwordResetUrl + "?token=" + token;
            String html = "<p>Hello " + (user.getFullName() != null ? user.getFullName() : "there") + ",</p>"
                    + "<p>We received a request to reset your ElderHive password. This link expires in "
                    + passwordResetExpiryMinutes + " minutes:</p>"
                    + "<p><a href=\"" + resetLink + "\">Reset your password</a></p>"
                    + "<p>If you did not request this, you can safely ignore this email.</p>";
            emailService.sendEmail(user.getEmail(), "Reset your ElderHive password", html);
        }

        return "If an account with that email exists, a password reset link has been sent.";
    }

    @Override
    @Transactional
    public String resetPassword(ResetPasswordRequest request) throws GenericException {
        // Not found, already used, or expired all collapse to the same TOKEN_INVALID —
        // same not-leaking-why principle as refresh()'s token validation.
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.TOKEN_INVALID, "Invalid or expired reset token."));

        if (resetToken.isUsed() || resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new GenericException(ExceptionCodeEnum.TOKEN_INVALID, "Invalid or expired reset token.");
        }

        if (request.getNewPassword() == null || request.getNewPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new GenericException(ExceptionCodeEnum.PASSWORD_TOO_WEAK,
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Security measure (explicit requirement): a password reset ends every other
        // active session too, in case the reset was triggered by a compromised account.
        refreshTokenService.revokeAllForUser(user.getId());

        return "Password reset successfully.";
    }
}
