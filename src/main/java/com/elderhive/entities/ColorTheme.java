package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import com.elderhive.converters.ColorPaletteConverter;
import com.elderhive.enums.ColorThemeStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Mirrors Portfolio's ColorTheme exactly — themeName uniqueness is checked at the service
 * layer (Prompt 42's ColorThemeService), not a DB unique constraint, same as Portfolio's
 * own version. palette is a plain POJO tree (ColorPalette -> ColorGroup -> ColorShade,
 * none of them JPA entities) serialized to a single TEXT column via ColorPaletteConverter.
 */
@Entity
@Table(name = "color_themes")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColorTheme extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String themeName;

    @Convert(converter = ColorPaletteConverter.class)
    @Column(columnDefinition = "TEXT")
    private ColorPalette palette;

    @Enumerated(EnumType.STRING)
    private ColorThemeStatusEnum status;
}
