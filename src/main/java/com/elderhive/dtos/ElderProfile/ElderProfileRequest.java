package com.elderhive.dtos.ElderProfile;

import com.elderhive.enums.GenderEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * familyUserId/elderUserId are present so an ADMIN caller can create a profile on behalf
 * of a specific family/elder pair, but a non-admin caller's values here are always
 * overwritten by the controller from their own authenticated identity (ElderProfileController
 * #resolveOwnership) — never trusted from the request body directly.
 */
@Data
public class ElderProfileRequest {

    private Long familyUserId;
    private Long elderUserId;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private LocalDate dateOfBirth;
    private GenderEnum gender;
    private String medicalConditions;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
}
