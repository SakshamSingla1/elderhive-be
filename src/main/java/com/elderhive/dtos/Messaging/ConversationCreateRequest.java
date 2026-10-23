package com.elderhive.dtos.Messaging;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationCreateRequest {

    @NotNull(message = "The other participant is required")
    private Long otherUserId;

    /** Optional - links the conversation to the booking it's about. */
    private Long bookingId;
}
