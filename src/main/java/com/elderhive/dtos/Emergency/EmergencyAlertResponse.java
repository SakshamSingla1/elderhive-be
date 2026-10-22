package com.elderhive.dtos.Emergency;

import com.elderhive.enums.EmergencyAlertStatusEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
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
    // Null until Prompt 84's claim/dispatch-override endpoint sets respondingCaretakerId.
    private String respondingCaretakerName;
    private Integer responseTimeSeconds;
    // Computed on every read (Prompt 84), never persisted: now - createdAt >
    // EMERGENCY_SETTINGS.escalationTimeoutMinutes (Prompt 37) && status != RESOLVED. Same
    // Lombok/Jackson double-"is"-stripping gotcha as NotificationResponse.isRead - Lombok's
    // getter for a field already named "isXxx" is isOverdue() (not isIsOverdue()), but
    // Jackson's own bean-property naming then strips that "is" AGAIN, serializing this as
    // "overdue" instead of "isOverdue" unless pinned explicitly like this.
    @Getter(onMethod_ = @__(@JsonProperty("isOverdue")))
    private boolean isOverdue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
