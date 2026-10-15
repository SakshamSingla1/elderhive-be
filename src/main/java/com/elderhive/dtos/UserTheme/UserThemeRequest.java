package com.elderhive.dtos.UserTheme;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserThemeRequest {

    @NotNull(message = "Theme ID is required")
    private Long themeId;
}
