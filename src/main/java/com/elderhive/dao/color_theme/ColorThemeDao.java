package com.elderhive.dao.color_theme;

import com.elderhive.entities.ColorTheme;
import com.elderhive.enums.ColorThemeStatusEnum;
import com.elderhive.repositories.ColorThemeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Slf4j
public class ColorThemeDao {

    private final ColorThemeRepository colorThemeRepository;

    public ColorThemeDao(ColorThemeRepository colorThemeRepository) {
        this.colorThemeRepository = colorThemeRepository;
    }

    public ColorTheme save(ColorTheme colorTheme) {
        return colorThemeRepository.save(colorTheme);
    }

    public Optional<ColorTheme> findById(Long id) {
        return colorThemeRepository.findById(id);
    }

    public void deleteById(Long id) {
        colorThemeRepository.deleteById(id);
    }

    public Optional<ColorTheme> findByThemeName(String themeName) {
        return colorThemeRepository.findByThemeName(themeName);
    }

    public Page<ColorTheme> findByCriteria(String search, ColorThemeStatusEnum status, Pageable pageable) {
        return colorThemeRepository.findByCriteria(search, status, pageable);
    }

    public Optional<ColorTheme> findFirstByStatusOrderByCreatedAtDesc(ColorThemeStatusEnum status) {
        return colorThemeRepository.findFirstByStatusOrderByCreatedAtDesc(status);
    }

    public void delete(ColorTheme colorTheme) {
        colorThemeRepository.delete(colorTheme);
    }
}
