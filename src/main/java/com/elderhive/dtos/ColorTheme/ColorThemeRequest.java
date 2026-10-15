package com.elderhive.dtos.ColorTheme;

import com.elderhive.enums.ColorThemeStatusEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColorThemeRequest {

    @NotBlank(message = "Theme name is required")
    private String themeName;

    private ColorPaletteDTO palette;

    private ColorThemeStatusEnum status;
}
