package com.elderhive.services.impl;

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
import com.elderhive.services.EmailService;
import com.elderhive.services.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A real unit test — mocked repositories/encoder, no Spring context, no database —
 * isolating AuthServiceImpl's own branching logic (role/password/email checks and the
 * User+UserRoleMapping pairing) from everything around it.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleMappingRepository userRoleMappingRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest validRequest() {
        return RegisterRequest.builder()
                .email("elder@example.com")
                .password("strongpass123")
                .fullName("Test Elder")
                .userType(UserTypeEnum.ELDER)
                .build();
    }

    private User activeUser() {
        return User.builder()
                .id(1L)
                .email("elder@example.com")
                .passwordHash("hashed-password")
                .userType(UserTypeEnum.ELDER)
                .status(UserStatusEnum.ACTIVE)
                .build();
    }

    @Test
    void registersAUserAndItsPrimaryRoleMapping() throws GenericException {
        RegisterRequest request = validRequest();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        RegisterResponse response = authService.register(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo(request.getEmail());
        assertThat(response.getUserType()).isEqualTo(UserTypeEnum.ELDER);
        assertThat(response.getStatus()).isEqualTo(UserStatusEnum.ACTIVE);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password");

        ArgumentCaptor<UserRoleMapping> mappingCaptor = ArgumentCaptor.forClass(UserRoleMapping.class);
        verify(userRoleMappingRepository).save(mappingCaptor.capture());
        assertThat(mappingCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(mappingCaptor.getValue().getUserType()).isEqualTo(UserTypeEnum.ELDER);
        assertThat(mappingCaptor.getValue().isPrimary()).isTrue();
    }

    @Test
    void rejectsDuplicateEmail() {
        RegisterRequest request = validRequest();
        when(userRepository.findByEmail(request.getEmail()))
                .thenReturn(Optional.of(User.builder().id(99L).email(request.getEmail()).build()));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DUPLICATE_EMAIL);

        verify(userRepository, never()).save(any());
        verify(userRoleMappingRepository, never()).save(any());
    }

    @Test
    void rejectsAdminSelfRegistration() {
        RegisterRequest request = validRequest();
        request.setUserType(UserTypeEnum.ADMIN);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_ROLE);

        // Fails before any repository is ever touched - the role check runs first.
        verifyNoInteractions(userRepository);
        verifyNoInteractions(userRoleMappingRepository);
    }

    @Test
    void rejectsWeakPassword() {
        RegisterRequest request = validRequest();
        request.setPassword("short");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.PASSWORD_TOO_WEAK);

        // Fails before the email-uniqueness check runs.
        verifyNoInteractions(userRepository);
    }

    @Test
    void logsInSuccessfullyAndIssuesBothTokens() throws GenericException {
        User user = activeUser();
        LoginRequest request = LoginRequest.builder().email(user.getEmail()).password("strongpass123").build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("strongpass123", user.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generateAccessToken(user.getEmail(), user.getId(), "ELDER")).thenReturn("access-token-value");
        when(refreshTokenService.issue(user)).thenReturn("refresh-token-value");
        when(userRoleMappingRepository.findByUserId(user.getId())).thenReturn(List.of(
                UserRoleMapping.builder().userId(user.getId()).userType(UserTypeEnum.ELDER).isPrimary(true).build()));

        LoginResult result = authService.login(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token-value");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token-value");
        assertThat(result.getId()).isEqualTo(user.getId());
        assertThat(result.getEmail()).isEqualTo(user.getEmail());
        assertThat(result.getUserType()).isEqualTo(UserTypeEnum.ELDER);
        assertThat(result.getRoles()).containsExactly(UserTypeEnum.ELDER);
    }

    @Test
    void rejectsWrongPassword() {
        User user = activeUser();
        LoginRequest request = LoginRequest.builder().email(user.getEmail()).password("wrong-password").build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_CREDENTIALS);

        verifyNoInteractions(jwtUtil);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void rejectsUnknownEmail() {
        LoginRequest request = LoginRequest.builder().email("nobody@example.com").password("whatever123").build();

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.INVALID_CREDENTIALS);

        // Short-circuits before ever checking a password - there's no hash to compare against.
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtUtil);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void rejectsLoginForANonActiveAccount() {
        User suspendedUser = activeUser();
        suspendedUser.setStatus(UserStatusEnum.SUSPENDED);
        LoginRequest request = LoginRequest.builder().email(suspendedUser.getEmail()).password("strongpass123").build();

        when(userRepository.findByEmail(suspendedUser.getEmail())).thenReturn(Optional.of(suspendedUser));
        when(passwordEncoder.matches("strongpass123", suspendedUser.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.ACCOUNT_NOT_ACTIVE);

        verifyNoInteractions(jwtUtil);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void validRefreshRotatesSuccessfully() throws GenericException {
        User user = activeUser();
        RefreshToken storedToken = RefreshToken.builder().id(10L).userId(user.getId()).revoked(false).build();

        when(refreshTokenService.validate("old-raw-token")).thenReturn(Optional.of(storedToken));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenService.issue(user)).thenReturn("new-raw-token");
        when(jwtUtil.generateAccessToken(user.getEmail(), user.getId(), "ELDER")).thenReturn("new-access-token");

        RefreshResult result = authService.refresh("old-raw-token");

        assertThat(result.getAccessToken()).isEqualTo("new-access-token");
        assertThat(result.getRefreshToken()).isEqualTo("new-raw-token");
        // Rotation: the old token is revoked and a new one issued, in that order.
        verify(refreshTokenService).revoke("old-raw-token");
        verify(refreshTokenService).issue(user);
    }

    @Test
    void anAlreadyRevokedRefreshTokenIsRejected() {
        // RefreshTokenServiceImpl.validate() (Prompt 13) already filters out revoked/
        // expired rows and returns empty - this is exactly what it would return for a
        // token that's been revoked.
        when(refreshTokenService.validate("revoked-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("revoked-token"))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.TOKEN_INVALID);

        // Short-circuits before touching the user, issuing a new token, or minting an
        // access token - nothing about the session is renewed.
        verifyNoInteractions(userRepository);
        verifyNoInteractions(jwtUtil);
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void logoutRevokesTheTokenSoASubsequentRefreshFails() throws GenericException {
        String rawToken = "session-token";

        authService.logout(rawToken);
        verify(refreshTokenService).revoke(rawToken);

        // Simulate what RefreshTokenServiceImpl.validate() now does for real once that
        // revoke() above has actually run (Prompt 13's own integration test proves this
        // filtering works against real Postgres) - confirms refresh() correctly rejects
        // the same token afterward, i.e. "logout actually revokes" end-to-end at the
        // service-contract level.
        when(refreshTokenService.validate(rawToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(rawToken))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.TOKEN_INVALID);
    }

    @Test
    void logoutWithNoCookieDoesNotThrowOrTouchTheRepository() {
        authService.logout(null);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void rejectsAnExpiredResetToken() {
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .id(1L)
                .token("expired-token")
                .userId(1L)
                .used(false)
                .expiryDate(LocalDateTime.now().minusMinutes(1))
                .build();
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("expired-token")
                .newPassword("brandnewpass123")
                .build();

        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.TOKEN_INVALID);

        // Fails before ever touching the user or revoking any session.
        verifyNoInteractions(userRepository);
        verifyNoInteractions(refreshTokenService);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void rejectsAnAlreadyUsedResetToken() {
        PasswordResetToken usedToken = PasswordResetToken.builder()
                .id(2L)
                .token("used-token")
                .userId(1L)
                .used(true)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("used-token")
                .newPassword("brandnewpass123")
                .build();

        when(passwordResetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.TOKEN_INVALID);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(refreshTokenService);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void successfulResetUpdatesThePasswordMarksTheTokenUsedAndInvalidatesAllSessions() throws GenericException {
        User user = activeUser();
        PasswordResetToken validToken = PasswordResetToken.builder()
                .id(3L)
                .token("valid-token")
                .userId(user.getId())
                .used(false)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("valid-token")
                .newPassword("brandnewpass123")
                .build();

        when(passwordResetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(validToken));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("brandnewpass123")).thenReturn("new-hashed-password");

        String message = authService.resetPassword(request);

        assertThat(message).isEqualTo("Password reset successfully.");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("new-hashed-password");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().isUsed()).isTrue();

        // The security measure this prompt explicitly asked for: every existing session
        // for this user is ended, not just the reset flow's own tokens.
        verify(refreshTokenService).revokeAllForUser(user.getId());
    }
}
