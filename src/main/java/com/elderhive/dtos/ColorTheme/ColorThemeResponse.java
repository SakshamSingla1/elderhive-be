package com.elderhive.dtos.ColorTheme;

import com.elderhive.enums.ColorThemeStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColorThemeResponse {

    private Long id;
    private String themeName;
    private ColorPaletteDTO palette;
    private ColorThemeStatusEnum status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
