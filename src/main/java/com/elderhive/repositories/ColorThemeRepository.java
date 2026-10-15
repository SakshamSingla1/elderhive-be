package com.elderhive.repositories;

import com.elderhive.entities.ColorTheme;
import com.elderhive.enums.ColorThemeStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ColorThemeRepository extends JpaRepository<ColorTheme, Long> {

    Optional<ColorTheme> findByThemeName(String themeName);

    @Query("""
            SELECT ct FROM ColorTheme ct
            WHERE (:search IS NULL OR :search = ''
                  OR LOWER(ct.themeName) LIKE CONCAT('%', LOWER(CAST(:search AS string)), '%'))
            AND (:status IS NULL OR ct.status = :status)
    """)
    Page<ColorTheme> findByCriteria(
            @Param("search") String search,
            @Param("status") ColorThemeStatusEnum status,
            Pageable pageable
    );

    Optional<ColorTheme> findFirstByStatusOrderByCreatedAtDesc(ColorThemeStatusEnum status);
}
