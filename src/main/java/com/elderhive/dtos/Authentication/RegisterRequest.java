package com.elderhive.dtos.Authentication;

import com.elderhive.enums.UserTypeEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * userType accepts any UserTypeEnum value structurally (so a bad value still produces a
 * clean 400 via Bean Validation / Jackson rather than a 500) — the actual
 * ELDER/CARETAKER/FAMILY_MEMBER-only restriction is enforced in AuthServiceImpl, which
 * throws GenericException(INVALID_ROLE) for ADMIN/SUPER_ADMIN, since that's a business
 * rule (not a structural one) and needs its own ExceptionCodeEnum/HTTP-status mapping.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    private String fullName;

    private String phone;

    @NotNull
    private UserTypeEnum userType;
}
