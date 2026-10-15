package com.elderhive.services;

import com.elderhive.dtos.UserTheme.UserThemeResponse;
import com.elderhive.exceptions.GenericException;

import java.util.List;

public interface UserThemeService {

    /**
     * Falls back to {@link ColorThemeService#getDefaultTheme()} when no UserThemeMapping
     * row exists for this user — a deliberate deviation from Portfolio's
     * getThemeByProfileId, which always required an existing ProfileThemeMapping and threw
     * DATA_NOT_FOUND otherwise. ElderHive's UserThemeMapping is a reverse-lookup OVERRIDE
     * table (see its own javadoc), not a mandatory per-user assignment: every user starts
     * on the platform default theme the moment their account exists, long before they ever
     * visit a settings page to pick one, so "no row yet" has to resolve successfully rather
     * than error.
     */
    UserThemeResponse getThemeByUserId(Long userId) throws GenericException;

    UserThemeResponse setThemeForUser(Long userId, Long themeId) throws GenericException;

    List<UserThemeResponse> getUsersByThemeId(Long themeId) throws GenericException;

    long countUsersByThemeId(Long themeId);

    String deleteThemeForUser(Long userId) throws GenericException;
}
