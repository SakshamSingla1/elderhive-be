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

    /**
     * <b>Design decision: deleting a theme users are actively on is ALLOWED, not blocked</b>
     * — deliberately, not by oversight. The alternative (reject the delete while
     * {@code countUsersByThemeId(id) > 0}) would force an admin pruning the catalog to
     * first go hunt down and reassign every affected user before they're allowed to
     * remove a theme nobody should be using anymore; this way, nobody is ever left on a
     * dangling reference or sees an error.
     *
     * <p>No extra code makes this work — it falls out of two independently-built pieces
     * actually composing correctly: {@code user_theme_mappings.theme_id} is
     * {@code ON DELETE CASCADE} (V10 migration, Prompt 41), so deleting the row here
     * silently removes every UserThemeMapping that pointed at it; and
     * UserThemeService#getThemeByUserId already treats "no mapping" as "use the default
     * theme," not an error (Prompt 44) — so a user whose chosen theme vanished out from
     * under them transparently resolves to the current default on their very next read,
     * with no 404 and no stale state. Verified end-to-end (real HTTP, real cascade, real
     * cache eviction) by ColorThemeDeletionFallbackIntegrationTest — including the
     * trickier case of deleting the theme that IS the currently-cached default, which is
     * exactly why both @CacheEvicts below still matter here.
     */
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
