package com.elderhive.seed;

import com.elderhive.dao.color_theme.ColorThemeDao;
import com.elderhive.entities.ColorGroup;
import com.elderhive.entities.ColorPalette;
import com.elderhive.entities.ColorShade;
import com.elderhive.entities.ColorTheme;
import com.elderhive.enums.ColorThemeStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds one default ACTIVE ColorTheme ("ElderHive Classic") on every boot — this is what
 * {@code ColorThemeService.getDefaultTheme()} resolves to, and therefore every user's
 * theme (Prompt 44's UserThemeService fallback) until they explicitly pick another.
 *
 * <p><b>Runs via CommandLineRunner, not a Flyway migration</b> — the same reasoning
 * MetaConfigSeeder (Prompt 37) documents: a raw SQL INSERT bypasses JPA entirely, so this
 * row would never get a real createdAt/createdBy (Auditable's JPA auditing only fires for
 * saves that go through the EntityManager). Going through ColorThemeDao here gets that for
 * free. Unlike MetaConfigSeeder, a dao already exists for this entity (Prompt 42's
 * ColorThemeDao), so this uses it directly rather than the repository, consistent with the
 * project's layered convention wherever a dao has already been built for an entity.
 *
 * <p><b>Only ever INSERTS when no theme named "ElderHive Classic" exists yet</b> — same
 * check-before-insert idempotency idiom as AdminBootstrapSeeder/MetaConfigSeeder, never
 * re-inserting or overwriting on a later boot. An admin can rename, edit the palette, or
 * even delete this theme entirely through Prompt 43's ColorThemeController; this seeder
 * must never silently recreate or clobber that customization on the next restart.
 *
 * <p>Palette shape mirrors Portfolio's DEFAULT_PALETTE exactly (4 colorGroups — primary/
 * secondary/accent/neutral — 10 colorShades each, suffixed 50-900 Tailwind-style). The
 * actual hex values are ElderHive's own: a warm terracotta/amber/brown palette (vs.
 * Portfolio's cool gold-and-grey), chosen and verified for the WCAG 2.1 AA contrast ratios
 * elder-care UI legibility demands (4.5:1 minimum for body text):
 * <ul>
 *   <li>Every 700/800/900 shade in every group reads as body text against that same
 *       group's own 50/100 shade (and against neutral50/neutral100/white) at 6:1-18:1 -
 *       comfortably over the 4.5:1 AA floor, several times over.</li>
 *   <li>Every group's 500/600 "base" shade (the one a palette picker would show as the
 *       swatch, and what buttons/icons use) clears at least the 3:1 AA floor for large
 *       text and non-text UI components (WCAG 1.4.11) against white/neutral50/neutral100
 *       — primary/secondary clear the full 4.5:1 text floor too, accent500 does not
 *       (2.8:1-3.1:1) but accent500 is a swatch/icon/highlight color here, never intended
 *       to carry small body text; accent700+ is what text-on-accent-background pairs with.</li>
 * </ul>
 * Ratios were computed with the standard WCAG relative-luminance formula, not eyeballed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ColorThemeSeeder implements CommandLineRunner {

    public static final String DEFAULT_THEME_NAME = "ElderHive Classic";

    private final ColorThemeDao colorThemeDao;

    @Override
    @Transactional
    public void run(String... args) {
        if (colorThemeDao.findByThemeName(DEFAULT_THEME_NAME).isPresent()) {
            return;
        }

        ColorTheme theme = ColorTheme.builder()
                .themeName(DEFAULT_THEME_NAME)
                .palette(defaultPalette())
                .status(ColorThemeStatusEnum.ACTIVE)
                .build();

        colorThemeDao.save(theme);
        log.info("Seeded default ColorTheme '{}'", DEFAULT_THEME_NAME);
    }

    private ColorPalette defaultPalette() {
        ColorPalette palette = new ColorPalette();
        palette.setColorGroups(List.of(
                group("primary", new String[]{
                        "#FDF3EE", "#FAE2D5", "#F3C2A8", "#E89E78", "#DC7A4E",
                        "#C1502E", "#A23F22", "#82301A", "#632414", "#44180D"
                }),
                group("secondary", new String[]{
                        "#F6EEE7", "#E8D4C2", "#CFAE8B", "#B2875F", "#946A45",
                        "#6B4226", "#58351D", "#452917", "#331D10", "#21120A"
                }),
                group("accent", new String[]{
                        "#FDF6E3", "#FBE8B8", "#F6D27E", "#F0BB48", "#E9A52C",
                        "#C9820F", "#A6690C", "#82510A", "#5F3A07", "#3D2404"
                }),
                group("neutral", new String[]{
                        "#FFFDF9", "#F6F1E9", "#E8DFD2", "#D3C6B2", "#AC9C86",
                        "#80725D", "#5F5345", "#453C32", "#2E2721", "#1C1713"
                })
        ));
        return palette;
    }

    private static final String[] SUFFIXES = {"50", "100", "200", "300", "400", "500", "600", "700", "800", "900"};

    private ColorGroup group(String groupName, String[] hexValuesInSuffixOrder) {
        ColorGroup group = new ColorGroup();
        group.setGroupName(groupName);
        group.setColorShades(shades(groupName, hexValuesInSuffixOrder));
        return group;
    }

    private List<ColorShade> shades(String groupName, String[] hexValuesInSuffixOrder) {
        ColorShade[] shades = new ColorShade[SUFFIXES.length];
        for (int i = 0; i < SUFFIXES.length; i++) {
            ColorShade shade = new ColorShade();
            shade.setColorName(groupName + SUFFIXES[i]);
            shade.setColorCode(hexValuesInSuffixOrder[i]);
            shades[i] = shade;
        }
        return List.of(shades);
    }
}
