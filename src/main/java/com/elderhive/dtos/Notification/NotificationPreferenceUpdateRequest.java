package com.elderhive.dtos.Notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** Bulk-upsert body for PUT /api/v1/notification-preferences — one or more type rows at once,
 *  matching how NotificationPreferences (FE) saves each toggle flip as a single-item list. */
@Data
public class NotificationPreferenceUpdateRequest {

    @NotEmpty(message = "At least one preference is required")
    @Valid
    private List<NotificationPreferenceItemRequest> preferences;
}
