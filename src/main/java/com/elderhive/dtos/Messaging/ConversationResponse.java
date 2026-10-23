package com.elderhive.dtos.Messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

    private Long id;
    private Long userAId;
    private Long userBId;

    // Resolved relative to whichever caller asked - the one of userAId/userBId that isn't
    // the caller themselves, so a listing can render "conversation with <name>" directly.
    private Long otherUserId;
    private String otherUserName;

    private Long bookingId;
    private LocalDateTime lastMessageAt;
    private Long unreadCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
