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
import com.elderhive.services.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

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
}
