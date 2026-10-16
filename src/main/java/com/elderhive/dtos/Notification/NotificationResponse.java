package com.elderhive.dtos.Notification;

import com.elderhive.enums.NotificationTypeEnum;
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
public class NotificationResponse {

    private Long id;
    private NotificationTypeEnum type;
    private String title;
    private String message;
    private String link;
    // Lombok's getter for a field already named "isXxx" is isRead() (not isIsRead()) - but
    // Jackson's own bean-property naming then strips that "is" prefix AGAIN, serializing
    // this as "read" instead of "isRead" unless pinned explicitly like this. Verified with
    // a throwaway probe test against this exact project's Jackson/Lombok versions before
    // fixing - without this, every notification-related JSON response (including the
    // /topic/notifications/{userId} WebSocket push) silently sent "read" instead of
    // "isRead", which the frontend's NotificationResponseDTO.isRead field would read as
    // always-undefined (-> always falsy -> every notification looked permanently unread).
    @Getter(onMethod_ = @__(@JsonProperty("isRead")))
    private boolean isRead;
    private LocalDateTime createdAt;
}
