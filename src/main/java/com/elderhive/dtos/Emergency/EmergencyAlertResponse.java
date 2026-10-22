package com.elderhive.dtos.Emergency;

import com.elderhive.enums.EmergencyAlertStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyAlertResponse {

    private Long id;
    private Long elderProfileId;
    // Denormalized from ElderProfile for display convenience, same precedent as
    // BookingResponse's elderName/caretakerName.
    private String elderName;
    private Long triggeredByUserId;
    private Double latitude;
    private Double longitude;
    private String address;
    private EmergencyAlertStatusEnum status;
    private Long respondingCaretakerId;
    // Always null coming out of Prompt 83's trigger() (nobody has claimed yet) - populated
    // once Prompt 84's claim endpoint sets respondingCaretakerId on an existing alert.
    private String respondingCaretakerName;
    private Integer responseTimeSeconds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
