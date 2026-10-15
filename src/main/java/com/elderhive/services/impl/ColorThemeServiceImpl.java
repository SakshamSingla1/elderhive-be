package com.elderhive.services.impl;

import com.elderhive.dao.color_theme.ColorThemeDao;
import com.elderhive.dtos.ColorTheme.ColorGroupDTO;
import com.elderhive.dtos.ColorTheme.ColorPaletteDTO;
import com.elderhive.dtos.ColorTheme.ColorShadeDTO;
import com.elderhive.dtos.ColorTheme.ColorThemeRequest;
import com.elderhive.dtos.ColorTheme.ColorThemeResponse;
import com.elderhive.entities.ColorGroup;
import com.elderhive.entities.ColorPalette;
import com.elderhive.entities.ColorShade;
import com.elderhive.entities.ColorTheme;
import com.elderhive.enums.ColorThemeStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.services.ColorThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mirrors Portfolio's ColorThemeServiceImpl directly, including its DTO<->entity mapping
 * structure. One deliberate deviation: Portfolio's updateTheme manually calls
 * theme.setUpdatedAt(LocalDateTime.now()) before saving — ElderHive's Auditable entities
 * (see its own javadoc) get updatedAt from JPA auditing automatically on every save, so
 * setting it manually here would be redundant at best and is the exact thing Auditable's
 * contract says never to do in a service.
 */
@Service
@RequiredArgsConstructor
public class ColorThemeServiceImpl implements ColorThemeService {

    private final ColorThemeDao colorThemeDao;

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "colorThemeDefault", allEntries = true),
            @CacheEvict(cacheNames = "colorThemeById", allEntries = true)
    })
    public ColorThemeResponse createTheme(ColorThemeRequest request) throws GenericException {
        String themeName = request.getThemeName();

        if (colorThemeDao.findByThemeName(themeName).isPresent()) {
            throw new GenericException(ExceptionCodeEnum.COLOR_THEME_ALREADY_EXISTS,
                    "Theme already exists for themeName '" + themeName + "'");
        }

        ColorTheme theme = ColorTheme.builder()
                .themeName(themeName)
                .palette(mapPaletteDtoToEntity(request.getPalette()))
                .status(ColorThemeStatusEnum.ACTIVE)
                .build();

        colorThemeDao.save(theme);

        return mapToResponse(theme);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "colorThemeDefault", allEntries = true),
            @CacheEvict(cacheNames = "colorThemeById", key = "#id")
    })
    public ColorThemeResponse updateTheme(Long id, ColorThemeRequest request) throws GenericException {
        ColorTheme theme = colorThemeDao.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.COLOR_THEME_NOT_FOUND, "Theme not found"));

        theme.setThemeName(request.getThemeName());
        theme.setPalette(mapPaletteDtoToEntity(request.getPalette()));
        theme.setStatus(request.getStatus());
        colorThemeDao.save(theme);
        return mapToResponse(theme);
    }

    @Override
    @Cacheable(cacheNames = "colorThemeById", key = "#themeId")
    public ColorThemeResponse getThemeById(Long themeId) throws GenericException {
        ColorTheme theme = colorThemeDao.findById(themeId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.COLOR_THEME_NOT_FOUND,
                        "Theme not found for id " + themeId));
        return mapToResponse(theme);
    }

    @Override
    public Page<ColorThemeResponse> getAllThemes(String search, ColorThemeStatusEnum status, Pageable pageable) {
        return colorThemeDao.findByCriteria(search, status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Cacheable(cacheNames = "colorThemeDefault")
    public ColorThemeResponse getDefaultTheme() throws GenericException {
        return colorThemeDao.findFirstByStatusOrderByCreatedAtDesc(ColorThemeStatusEnum.ACTIVE)
                .map(this::mapToResponse)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.COLOR_THEME_NOT_FOUND,
                        "No active default theme found"));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "colorThemeDefault", allEntries = true),
            @CacheEvict(cacheNames = "colorThemeById", key = "#id")
    })
    public String deleteTheme(Long id) throws GenericException {
        ColorTheme theme = colorThemeDao.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.COLOR_THEME_NOT_FOUND, "Theme not found"));
        colorThemeDao.delete(theme);
        return "Theme deleted successfully";
    }

    private ColorPalette mapPaletteDtoToEntity(ColorPaletteDTO dto) {
        if (dto == null) return null;

        ColorPalette palette = new ColorPalette();
        List<ColorGroup> groups = dto.getColorGroups() == null ? List.of()
                : dto.getColorGroups().stream().map(this::mapGroupDtoToEntity).collect(Collectors.toList());
        palette.setColorGroups(groups);
        return palette;
    }

    private ColorGroup mapGroupDtoToEntity(ColorGroupDTO dto) {
        if (dto == null) return null;
        ColorGroup group = new ColorGroup();
        group.setGroupName(dto.getGroupName());
        List<ColorShade> shades = dto.getColorShades() == null ? List.of()
                : dto.getColorShades().stream().map(this::mapShadeDtoToEntity).collect(Collectors.toList());
        group.setColorShades(shades);
        return group;
    }

    private ColorShade mapShadeDtoToEntity(ColorShadeDTO dto) {
        if (dto == null) return null;
        ColorShade shade = new ColorShade();
        shade.setColorName(dto.getColorName());
        shade.setColorCode(dto.getColorCode());
        return shade;
    }

    private ColorThemeResponse mapToResponse(ColorTheme theme) {
        ColorPaletteDTO paletteDTO = new ColorPaletteDTO();
        paletteDTO.setColorGroups(
                theme.getPalette() == null ? List.of()
                        : theme.getPalette().getColorGroups().stream()
                                .map(this::mapGroupEntityToDto)
                                .collect(Collectors.toList()));

        return ColorThemeResponse.builder()
                .id(theme.getId())
                .themeName(theme.getThemeName())
                .palette(paletteDTO)
                .status(theme.getStatus())
                .createdAt(theme.getCreatedAt())
                .updatedAt(theme.getUpdatedAt())
                .createdBy(theme.getCreatedBy())
                .updatedBy(theme.getUpdatedBy())
                .build();
    }

    private ColorGroupDTO mapGroupEntityToDto(ColorGroup group) {
        ColorGroupDTO dto = new ColorGroupDTO();
        dto.setGroupName(group.getGroupName());
        dto.setColorShades(
                group.getColorShades() == null ? List.of()
                        : group.getColorShades().stream()
                                .map(this::mapShadeEntityToDto)
                                .collect(Collectors.toList()));
        return dto;
    }

    private ColorShadeDTO mapShadeEntityToDto(ColorShade shade) {
        ColorShadeDTO dto = new ColorShadeDTO();
        dto.setColorName(shade.getColorName());
        dto.setColorCode(shade.getColorCode());
        return dto;
    }
}
