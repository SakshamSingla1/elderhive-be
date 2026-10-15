package com.elderhive.entities;

import com.elderhive.enums.ColorThemeStatusEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Runs against the real local Postgres (docker-compose.yml). No ColorThemeRepository
 * exists yet (that's Prompt 42) — persists directly via the shared EntityManager instead,
 * proving ColorPaletteConverter's JSON round-trip genuinely survives a real DB flush+clear
 * (not just returning the same in-memory ColorPalette object back), the actual risky
 * behavior this prompt introduces.
 */
@SpringBootTest
@Transactional
class ColorThemeTest {

    @Autowired
    private EntityManager entityManager;

    private ColorPalette samplePalette() {
        ColorPalette palette = new ColorPalette();
        ColorShade shade1 = new ColorShade();
        shade1.setColorName("primary500");
        shade1.setColorCode("#2E7D32");
        ColorShade shade2 = new ColorShade();
        shade2.setColorName("primary600");
        shade2.setColorCode("#1B5E20");
        ColorGroup group = new ColorGroup();
        group.setGroupName("primary");
        group.setColorShades(List.of(shade1, shade2));
        palette.setColorGroups(List.of(group));
        return palette;
    }

    @Test
    void persistsAndReloadsTheFullPaletteTreeThroughARealDatabaseRoundTrip() {
        ColorTheme theme = ColorTheme.builder()
                .themeName("Forest Green")
                .palette(samplePalette())
                .status(ColorThemeStatusEnum.ACTIVE)
                .build();

        entityManager.persist(theme);
        entityManager.flush();
        Long id = theme.getId();
        assertThat(id).isNotNull();
        assertThat(theme.getCreatedAt()).isNotNull();

        // Forces a genuine round trip through the DB column, not just handing back the
        // same in-memory ColorPalette object the converter was never actually exercised on.
        entityManager.clear();

        ColorTheme reloaded = entityManager.find(ColorTheme.class, id);
        assertThat(reloaded.getThemeName()).isEqualTo("Forest Green");
        assertThat(reloaded.getStatus()).isEqualTo(ColorThemeStatusEnum.ACTIVE);
        assertThat(reloaded.getPalette()).isNotNull();
        assertThat(reloaded.getPalette().getColorGroups()).hasSize(1);

        ColorGroup reloadedGroup = reloaded.getPalette().getColorGroups().get(0);
        assertThat(reloadedGroup.getGroupName()).isEqualTo("primary");
        assertThat(reloadedGroup.getColorShades())
                .extracting(ColorShade::getColorName, ColorShade::getColorCode)
                .containsExactly(
                        tuple("primary500", "#2E7D32"),
                        tuple("primary600", "#1B5E20"));
    }

    @Test
    void persistsAndReloadsANullPaletteAsNull() {
        ColorTheme theme = ColorTheme.builder()
                .themeName("Empty Theme")
                .palette(null)
                .status(ColorThemeStatusEnum.INACTIVE)
                .build();

        entityManager.persist(theme);
        entityManager.flush();
        Long id = theme.getId();
        entityManager.clear();

        ColorTheme reloaded = entityManager.find(ColorTheme.class, id);
        assertThat(reloaded.getPalette()).isNull();
    }

    @Test
    void thePersistedColumnIsGenuineJsonNotASerializedJavaObject() {
        ColorTheme theme = ColorTheme.builder()
                .themeName("JSON Shape Check")
                .palette(samplePalette())
                .status(ColorThemeStatusEnum.ACTIVE)
                .build();

        entityManager.persist(theme);
        entityManager.flush();

        String rawPalette = (String) entityManager
                .createNativeQuery("SELECT palette FROM color_themes WHERE id = :id")
                .setParameter("id", theme.getId())
                .getSingleResult();

        assertThat(rawPalette).startsWith("{\"colorGroups\"");
        assertThat(rawPalette).contains("\"groupName\":\"primary\"");
        assertThat(rawPalette).contains("\"colorCode\":\"#2E7D32\"");
    }

    @Test
    void allowsDuplicateThemeNamesAtTheDatabaseLevel() {
        // Explicitly NOT a DB unique constraint (matches Portfolio exactly) - uniqueness
        // is a service-layer concern for Prompt 42's ColorThemeService to enforce.
        entityManager.persist(ColorTheme.builder().themeName("Same Name").status(ColorThemeStatusEnum.ACTIVE).build());
        entityManager.flush();

        entityManager.persist(ColorTheme.builder().themeName("Same Name").status(ColorThemeStatusEnum.ACTIVE).build());
        entityManager.flush();

        Long count = (Long) entityManager
                .createQuery("SELECT COUNT(c) FROM ColorTheme c WHERE c.themeName = :name")
                .setParameter("name", "Same Name")
                .getSingleResult();
        assertThat(count).isEqualTo(2L);
    }
}
