package com.elderhive.dtos.Notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * Body for the temporary POST /api/v1/notifications/test stub (Prompt 59) - a direct pass
 * through to NotificationService.send(), nothing more.
 */
@Data
public class NotificationTestRequest {

    @NotBlank(message = "Template key is required")
    private String templateKey;

    @NotNull(message = "Variables map is required (use {} for a template with no placeholders)")
    private Map<String, String> variables;

    @NotNull(message = "Target user ID is required")
    private Long targetUserId;
}
