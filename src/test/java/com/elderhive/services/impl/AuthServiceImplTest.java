package com.elderhive.services.impl;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
}
