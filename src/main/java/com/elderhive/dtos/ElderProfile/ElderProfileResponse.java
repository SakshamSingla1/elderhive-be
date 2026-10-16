package com.elderhive.dtos.ElderProfile;

import com.elderhive.enums.GenderEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElderProfileResponse {

    private Long id;
    private Long familyUserId;
    private Long elderUserId;
    private String fullName;
    private LocalDate dateOfBirth;
    private GenderEnum gender;
    private String medicalConditions;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
