package com.elderhive.repositories;

import com.elderhive.entities.UserThemeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserThemeMappingRepository extends JpaRepository<UserThemeMapping, Long> {

    Optional<UserThemeMapping> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);

    List<UserThemeMapping> findAllByThemeId(Long themeId);

    long countByThemeId(Long themeId);
}
