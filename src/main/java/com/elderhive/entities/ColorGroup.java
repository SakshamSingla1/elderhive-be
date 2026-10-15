package com.elderhive.entities;

import lombok.Data;

import java.util.List;

@Data
public class ColorGroup {
    private String groupName;
    private List<ColorShade> colorShades;
}
