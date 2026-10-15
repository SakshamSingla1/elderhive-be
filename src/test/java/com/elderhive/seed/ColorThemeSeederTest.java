package com.elderhive.seed;

import com.elderhive.entities.ColorGroup;
import com.elderhive.entities.ColorShade;
import com.elderhive.entities.ColorTheme;
import com.elderhive.enums.ColorThemeStatusEnum;
import com.elderhive.repositories.ColorThemeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres — the seeder already ran once automatically as a
 * CommandLineRunner during this test class's own context startup (same caveat
 * MetaConfigSeederTest documents), so the explicit run() calls below are IN ADDITION to
 * that, proving idempotency regardless of how many times it's actually invoked.
 */
@SpringBootTest
@Transactional
class ColorThemeSeederTest {

    @Autowired
    private ColorThemeSeeder colorThemeSeeder;

    @Autowired
    private ColorThemeRepository colorThemeRepository;

    @Test
    void seedsOneActiveThemeWithFourGroupsOfTenShadesEach() {
        colorThemeSeeder.run();

        ColorTheme theme = colorThemeRepository.findByThemeName(ColorThemeSeeder.DEFAULT_THEME_NAME).orElseThrow();
        assertThat(theme.getStatus()).isEqualTo(ColorThemeStatusEnum.ACTIVE);

        List<ColorGroup> groups = theme.getPalette().getColorGroups();
        assertThat(groups).extracting(ColorGroup::getGroupName)
                .containsExactlyInAnyOrder("primary", "secondary", "accent", "neutral");

        for (ColorGroup group : groups) {
            assertThat(group.getColorShades()).hasSize(10);
            List<String> expectedNames = List.of("50", "100", "200", "300", "400", "500", "600", "700", "800", "900")
                    .stream().map(suffix -> group.getGroupName() + suffix).collect(Collectors.toList());
            assertThat(group.getColorShades()).extracting(ColorShade::getColorName)
                    .containsExactlyInAnyOrderElementsOf(expectedNames);
            assertThat(group.getColorShades()).allMatch(shade -> shade.getColorCode().matches("^#[0-9A-Fa-f]{6}$"));
        }
    }

    @Test
    void runningTheSeederRepeatedlyNeverDuplicatesTheDefaultTheme() {
        colorThemeSeeder.run();
        long countAfterFirst = colorThemeRepository.count();

        colorThemeSeeder.run();
        colorThemeSeeder.run();

        assertThat(colorThemeRepository.count()).isEqualTo(countAfterFirst);
        assertThat(colorThemeRepository.findByThemeName(ColorThemeSeeder.DEFAULT_THEME_NAME)).isPresent();
    }

    @Test
    void neverOverwritesTheDefaultThemeAfterAnAdminHasCustomizedIt() {
        colorThemeSeeder.run();
        ColorTheme theme = colorThemeRepository.findByThemeName(ColorThemeSeeder.DEFAULT_THEME_NAME).orElseThrow();
        long countBeforeEdit = colorThemeRepository.count();

        // Simulates an admin editing this theme's palette via ColorThemeController after
        // boot, keeping the same themeName (the seeder's own idempotency key).
        theme.setStatus(ColorThemeStatusEnum.INACTIVE);
        colorThemeRepository.save(theme);

        colorThemeSeeder.run();

        ColorTheme reloaded = colorThemeRepository.findByThemeName(ColorThemeSeeder.DEFAULT_THEME_NAME).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ColorThemeStatusEnum.INACTIVE);
        assertThat(colorThemeRepository.count()).isEqualTo(countBeforeEdit);
    }

    /**
     * Verifies the WCAG 2.1 relative-luminance contrast formula directly against the
     * seeded hex values, rather than trusting the seeder's own javadoc claims. Every
     * 700/800/900 "text" shade in every group must clear the 4.5:1 AA floor against that
     * same group's own lightest (50/100) shade — the realistic dark-text-on-light-
     * background pairing this palette is designed for.
     */
    @Test
    void everyGroupsDarkShadesClearWcagAaContrastAgainstItsOwnLightShades() {
        colorThemeSeeder.run();
        ColorTheme theme = colorThemeRepository.findByThemeName(ColorThemeSeeder.DEFAULT_THEME_NAME).orElseThrow();

        for (ColorGroup group : theme.getPalette().getColorGroups()) {
            Map<String, String> hexByShade = group.getColorShades().stream()
                    .collect(Collectors.toMap(ColorShade::getColorName, ColorShade::getColorCode));

            String lightest = hexByShade.get(group.getGroupName() + "50");
            for (String suffix : List.of("700", "800", "900")) {
                String dark = hexByShade.get(group.getGroupName() + suffix);
                double ratio = contrastRatio(dark, lightest);
                assertThat(ratio)
                        .as("%s%s on %s50 should clear WCAG AA (4.5:1) for body text", group.getGroupName(), suffix, group.getGroupName())
                        .isGreaterThanOrEqualTo(4.5);
            }
        }
    }

    private static double contrastRatio(String hex1, String hex2) {
        double l1 = relativeLuminance(hex1);
        double l2 = relativeLuminance(hex2);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b);
    }

    private static double linearize(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
