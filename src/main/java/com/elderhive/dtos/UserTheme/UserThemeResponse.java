package com.elderhive.dtos.UserTheme;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mirrors Portfolio's ProfileThemeResponse's shape (id/userId/a display field/themeId/
 * themeName) but with ElderHive's own direct-field audit convention (matching
 * ColorThemeResponse/RoleResponse) instead of Portfolio's AuditableResponse/Helper.setAudit.
 * {@code id}/{@code createdAt}/{@code updatedAt}/{@code createdBy}/{@code updatedBy} are
 * null when this response was resolved via the default-theme fallback (no persisted
 * UserThemeMapping row for this user) rather than a real mapping.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserThemeResponse {

    private Long id;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private Long themeId;
    private String themeName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
