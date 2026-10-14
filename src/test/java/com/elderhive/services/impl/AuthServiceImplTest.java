package com.elderhive.services.impl;

import com.elderhive.dtos.Authentication.LoginRequest;
import com.elderhive.dtos.Authentication.LoginResult;
import com.elderhive.dtos.Authentication.RegisterRequest;
import com.elderhive.dtos.Authentication.RegisterResponse;
import com.elderhive.entities.User;
import com.elderhive.entities.UserRoleMapping;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.UserRepository;
import com.elderhive.repositories.UserRoleMappingRepository;
import com.elderhive.security.JwtUtil;
import com.elderhive.services.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
}
