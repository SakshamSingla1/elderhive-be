package com.elderhive.dtos.CaretakerProfile;

import com.elderhive.dtos.ServiceCategory.ServiceCategoryResponse;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/** fullName/email/phone are denormalized from User - saves a caller a second lookup when
 *  browsing caretaker profiles (matches ElderSphere's own response shape). specialties is
 *  the full ServiceCategoryResponse per entry (Prompt 72), not just ids, so a caller can
 *  render the category's name/icon without a second lookup either. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaretakerProfileResponse {

    private Long id;
    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private String bio;
    private Set<ServiceCategoryResponse> specialties;
    private Integer yearsOfExperience;
    private BigDecimal hourlyRate;
    private Double ratingAverage;
    private CaretakerVerificationStatusEnum verificationStatus;
    private Long profilePhotoFileAssetId;
    private Double latitude;
    private Double longitude;
    private Double serviceRadiusKm;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
