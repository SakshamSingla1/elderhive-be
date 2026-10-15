package com.elderhive.services;

import com.elderhive.dtos.ColorTheme.ColorThemeRequest;
import com.elderhive.dtos.ColorTheme.ColorThemeResponse;
import com.elderhive.enums.ColorThemeStatusEnum;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ColorThemeService {

    ColorThemeResponse createTheme(ColorThemeRequest request) throws GenericException;

    ColorThemeResponse updateTheme(Long id, ColorThemeRequest request) throws GenericException;

    ColorThemeResponse getThemeById(Long themeId) throws GenericException;

    Page<ColorThemeResponse> getAllThemes(String search, ColorThemeStatusEnum status, Pageable pageable);

    /** The first ACTIVE theme ordered by createdAt desc — what a user with no
     *  UserThemeMapping row resolves to (Prompt 44's UserThemeService). */
    ColorThemeResponse getDefaultTheme() throws GenericException;

    String deleteTheme(Long id) throws GenericException;
}
