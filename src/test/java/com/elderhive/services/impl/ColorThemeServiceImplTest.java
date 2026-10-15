package com.elderhive.services.impl;

import com.elderhive.dao.color_theme.ColorThemeDao;
import com.elderhive.dtos.ColorTheme.ColorGroupDTO;
import com.elderhive.dtos.ColorTheme.ColorPaletteDTO;
import com.elderhive.dtos.ColorTheme.ColorShadeDTO;
import com.elderhive.dtos.ColorTheme.ColorThemeRequest;
import com.elderhive.dtos.ColorTheme.ColorThemeResponse;
import com.elderhive.entities.ColorTheme;
import com.elderhive.enums.ColorThemeStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColorThemeServiceImplTest {

    @Mock
    private ColorThemeDao colorThemeDao;

    @InjectMocks
    private ColorThemeServiceImpl colorThemeService;

    private ColorPaletteDTO samplePaletteDto() {
        ColorShadeDTO shade = new ColorShadeDTO();
        shade.setColorName("primary500");
        shade.setColorCode("#2E7D32");
        ColorGroupDTO group = new ColorGroupDTO();
        group.setGroupName("primary");
        group.setColorShades(List.of(shade));
        ColorPaletteDTO palette = new ColorPaletteDTO();
        palette.setColorGroups(List.of(group));
        return palette;
    }

    @Test
    void createThemeSavesANewThemeWithTheNestedPaletteMappedCorrectly() throws GenericException {
        ColorThemeRequest request = ColorThemeRequest.builder()
                .themeName("Forest Green")
                .palette(samplePaletteDto())
                .build();
        when(colorThemeDao.findByThemeName("Forest Green")).thenReturn(Optional.empty());
        when(colorThemeDao.save(any(ColorTheme.class))).thenAnswer(invocation -> {
            ColorTheme saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ColorThemeResponse response = colorThemeService.createTheme(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getThemeName()).isEqualTo("Forest Green");
        assertThat(response.getStatus()).isEqualTo(ColorThemeStatusEnum.ACTIVE);
        assertThat(response.getPalette().getColorGroups()).hasSize(1);
        assertThat(response.getPalette().getColorGroups().get(0).getGroupName()).isEqualTo("primary");
        assertThat(response.getPalette().getColorGroups().get(0).getColorShades())
                .extracting(ColorShadeDTO::getColorName, ColorShadeDTO::getColorCode)
                .containsExactly(tuple("primary500", "#2E7D32"));

        ArgumentCaptor<ColorTheme> captor = ArgumentCaptor.forClass(ColorTheme.class);
        verify(colorThemeDao).save(captor.capture());
        assertThat(captor.getValue().getPalette().getColorGroups()).hasSize(1);
    }

    @Test
    void createThemeRejectsADuplicateThemeName() {
        ColorThemeRequest request = ColorThemeRequest.builder().themeName("Existing").build();
        when(colorThemeDao.findByThemeName("Existing"))
                .thenReturn(Optional.of(ColorTheme.builder().id(5L).themeName("Existing").build()));

        assertThatThrownBy(() -> colorThemeService.createTheme(request))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.COLOR_THEME_ALREADY_EXISTS);

        verify(colorThemeDao, never()).save(any());
    }

    @Test
    void updateThemeUpdatesAnExistingThemeInPlace() throws GenericException {
        ColorTheme existing = ColorTheme.builder().id(3L).themeName("Old Name").status(ColorThemeStatusEnum.ACTIVE).build();
        when(colorThemeDao.findById(3L)).thenReturn(Optional.of(existing));
        when(colorThemeDao.save(any(ColorTheme.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ColorThemeRequest request = ColorThemeRequest.builder()
                .themeName("New Name")
                .status(ColorThemeStatusEnum.INACTIVE)
                .build();

        ColorThemeResponse response = colorThemeService.updateTheme(3L, request);

        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getThemeName()).isEqualTo("New Name");
        assertThat(response.getStatus()).isEqualTo(ColorThemeStatusEnum.INACTIVE);
    }

    @Test
    void updateThemeThrowsWhenTheThemeDoesNotExist() {
        when(colorThemeDao.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> colorThemeService.updateTheme(99L, ColorThemeRequest.builder().themeName("X").build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.COLOR_THEME_NOT_FOUND);

        verify(colorThemeDao, never()).save(any());
    }

    @Test
    void getThemeByIdThrowsWhenNotFound() {
        when(colorThemeDao.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> colorThemeService.getThemeById(42L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.COLOR_THEME_NOT_FOUND);
    }

    @Test
    void getAllThemesDelegatesToDaoAndMapsEachResult() {
        ColorTheme theme = ColorTheme.builder().id(1L).themeName("A").status(ColorThemeStatusEnum.ACTIVE).build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<ColorTheme> page = new PageImpl<>(List.of(theme), pageable, 1);
        when(colorThemeDao.findByCriteria("forest", ColorThemeStatusEnum.ACTIVE, pageable)).thenReturn(page);

        Page<ColorThemeResponse> result = colorThemeService.getAllThemes("forest", ColorThemeStatusEnum.ACTIVE, pageable);

        assertThat(result.getContent()).extracting(ColorThemeResponse::getThemeName).containsExactly("A");
    }

    @Test
    void getDefaultThemeReturnsTheFirstActiveThemeOrderedByCreatedAtDesc() throws GenericException {
        ColorTheme theme = ColorTheme.builder().id(7L).themeName("Default One").status(ColorThemeStatusEnum.ACTIVE).build();
        when(colorThemeDao.findFirstByStatusOrderByCreatedAtDesc(ColorThemeStatusEnum.ACTIVE)).thenReturn(Optional.of(theme));

        ColorThemeResponse response = colorThemeService.getDefaultTheme();

        assertThat(response.getThemeName()).isEqualTo("Default One");
    }

    @Test
    void getDefaultThemeThrowsWhenNoActiveThemeExists() {
        when(colorThemeDao.findFirstByStatusOrderByCreatedAtDesc(ColorThemeStatusEnum.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> colorThemeService.getDefaultTheme())
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.COLOR_THEME_NOT_FOUND);
    }

    @Test
    void deleteThemeRemovesAnExistingTheme() throws GenericException {
        ColorTheme theme = ColorTheme.builder().id(9L).themeName("To Delete").build();
        when(colorThemeDao.findById(9L)).thenReturn(Optional.of(theme));

        String message = colorThemeService.deleteTheme(9L);

        assertThat(message).isEqualTo("Theme deleted successfully");
        verify(colorThemeDao).delete(theme);
    }

    @Test
    void deleteThemeThrowsWhenNotFound() {
        when(colorThemeDao.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> colorThemeService.deleteTheme(123L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.COLOR_THEME_NOT_FOUND);

        verify(colorThemeDao, never()).delete(any());
    }

    @Test
    void mapsANullPaletteToAnEmptyGroupListRatherThanNullOrThrowing() throws GenericException {
        ColorTheme theme = ColorTheme.builder().id(1L).themeName("No Palette").palette(null).build();
        when(colorThemeDao.findById(1L)).thenReturn(Optional.of(theme));

        ColorThemeResponse response = colorThemeService.getThemeById(1L);

        assertThat(response.getPalette().getColorGroups()).isEmpty();
    }
}
