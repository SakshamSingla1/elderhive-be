package com.elderhive.dtos.Messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deliberately has no {@code @NotNull}/{@code @NotBlank} on content - a content-less,
 * attachment-only message is valid (MessagingServiceImpl#sendMessage enforces "content or
 * attachment, at least one" itself, since that's an either/or rule bean validation can't
 * express cleanly). Carries no senderId/conversationId - both come from the caller's own
 * identity and the STOMP destination/path, never from client-supplied payload fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    private String content;

    /** Optional, previously uploaded via the existing generic file-upload endpoint. */
    private Long fileAssetId;
}
