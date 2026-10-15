package com.elderhive.dtos.ColorTheme;

import lombok.Data;

import java.util.List;

@Data
public class ColorGroupDTO {
    private String groupName;
    private List<ColorShadeDTO> colorShades;
}
