package com.elderhive.dao.user_theme;

import com.elderhive.entities.UserThemeMapping;
import com.elderhive.repositories.UserThemeMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class UserThemeMappingDao {

    private final UserThemeMappingRepository userThemeMappingRepository;

    public UserThemeMappingDao(UserThemeMappingRepository userThemeMappingRepository) {
        this.userThemeMappingRepository = userThemeMappingRepository;
    }

    public UserThemeMapping save(UserThemeMapping mapping) {
        return userThemeMappingRepository.save(mapping);
    }

    public Optional<UserThemeMapping> findById(Long id) {
        return userThemeMappingRepository.findById(id);
    }

    public void deleteById(Long id) {
        userThemeMappingRepository.deleteById(id);
    }

    public Optional<UserThemeMapping> findByUserId(Long userId) {
        return userThemeMappingRepository.findByUserId(userId);
    }

    public boolean existsByUserId(Long userId) {
        return userThemeMappingRepository.existsByUserId(userId);
    }

    @Transactional
    public void deleteByUserId(Long userId) {
        userThemeMappingRepository.deleteByUserId(userId);
    }

    public List<UserThemeMapping> findAllByThemeId(Long themeId) {
        return userThemeMappingRepository.findAllByThemeId(themeId);
    }

    public long countByThemeId(Long themeId) {
        return userThemeMappingRepository.countByThemeId(themeId);
    }
}
