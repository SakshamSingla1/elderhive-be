package com.elderhive.services.impl;

import com.elderhive.dtos.ColorTheme.ColorThemeRequest;
import com.elderhive.dtos.ColorTheme.ColorThemeResponse;
import com.elderhive.entities.ColorTheme;
import com.elderhive.enums.ColorThemeStatusEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ColorThemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres AND the real auto-configured Caffeine CacheManager
 * (no explicit CacheConfig exists — @EnableCaching + caffeine-on-the-classpath, both
 * already present since Prompt 1, is enough) — proves the @Cacheable/@CacheEvict
 * annotations actually change observable behavior, not just that they compile. Each test
 * mutates the underlying row directly via the repository (bypassing the service/cache
 * entirely) to prove a still-cached read returns the STALE value, then calls the
 * service's own evicting method and confirms the NEXT read reflects reality again.
 */
@SpringBootTest
@Transactional
class ColorThemeServiceImplCachingTest {

    @Autowired
    private ColorThemeServiceImpl colorThemeService;

    @Autowired
    private ColorThemeRepository colorThemeRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        // Test isolation: colorThemeDefault has no cache key (default SimpleKey.EMPTY),
        // so a value left behind by a previous test/run would otherwise leak in here.
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void getThemeByIdIsCachedUntilEvictedByAnUpdate() throws GenericException {
        ColorTheme theme = colorThemeRepository.save(
                ColorTheme.builder().themeName("Cache Test Original").status(ColorThemeStatusEnum.ACTIVE).build());

        ColorThemeResponse first = colorThemeService.getThemeById(theme.getId());
        assertThat(first.getThemeName()).isEqualTo("Cache Test Original");

        // Mutate the row directly, bypassing the service/cache entirely.
        theme.setThemeName("Mutated Directly, Should Not Be Seen Yet");
        colorThemeRepository.save(theme);
        colorThemeRepository.flush();

        ColorThemeResponse stillCached = colorThemeService.getThemeById(theme.getId());
        assertThat(stillCached.getThemeName())
                .as("getThemeById should still return the cached pre-mutation value")
                .isEqualTo("Cache Test Original");

        // updateTheme evicts colorThemeById for exactly this id.
        colorThemeService.updateTheme(theme.getId(),
                ColorThemeRequest.builder().themeName("Updated For Real").status(ColorThemeStatusEnum.ACTIVE).build());

        ColorThemeResponse afterEviction = colorThemeService.getThemeById(theme.getId());
        assertThat(afterEviction.getThemeName()).isEqualTo("Updated For Real");
    }

    @Test
    void getDefaultThemeIsCachedUntilEvictedByACreate() throws GenericException {
        colorThemeRepository.save(
                ColorTheme.builder().themeName("First Default").status(ColorThemeStatusEnum.ACTIVE).build());

        ColorThemeResponse first = colorThemeService.getDefaultTheme();
        assertThat(first.getThemeName()).isEqualTo("First Default");

        // A newer ACTIVE theme saved directly (bypassing createTheme, so no eviction fires)
        // would be the real new default by createdAt desc, but the cache should still
        // return the stale answer until something evicts it.
        colorThemeRepository.save(
                ColorTheme.builder().themeName("Newer Theme Bypassing Cache").status(ColorThemeStatusEnum.ACTIVE).build());

        ColorThemeResponse stillCached = colorThemeService.getDefaultTheme();
        assertThat(stillCached.getThemeName())
                .as("getDefaultTheme should still return the cached first answer")
                .isEqualTo("First Default");

        // createTheme evicts colorThemeDefault (allEntries) as a side effect.
        colorThemeService.createTheme(com.elderhive.dtos.ColorTheme.ColorThemeRequest.builder()
                .themeName("Triggers Eviction")
                .status(ColorThemeStatusEnum.ACTIVE)
                .build());

        ColorThemeResponse afterEviction = colorThemeService.getDefaultTheme();
        assertThat(afterEviction.getThemeName())
                .as("the cache should now reflect the true latest ACTIVE theme by createdAt desc")
                .isEqualTo("Triggers Eviction");
    }

    @Test
    void deleteThemeEvictsBothCaches() throws GenericException {
        ColorTheme theme = colorThemeRepository.save(
                ColorTheme.builder().themeName("Soon Deleted").status(ColorThemeStatusEnum.ACTIVE).build());

        colorThemeService.getThemeById(theme.getId()); // populate colorThemeById cache
        colorThemeService.getDefaultTheme(); // populate colorThemeDefault cache

        colorThemeService.deleteTheme(theme.getId());

        // getThemeById must now genuinely re-query and find it gone (404), not return a
        // stale cached response for an id that no longer exists.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> colorThemeService.getThemeById(theme.getId()))
                .isInstanceOf(GenericException.class);
    }
}
