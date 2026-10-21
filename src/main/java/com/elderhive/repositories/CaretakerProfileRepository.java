package com.elderhive.repositories;

import com.elderhive.entities.CaretakerProfile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaretakerProfileRepository extends JpaRepository<CaretakerProfile, Long> {

    Optional<CaretakerProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    // A plain ON-clause join against User's scalar userId field (no @ManyToOne mapping
    // between these entities exists), matching ElderProfileRepository's own established
    // join pattern. Backs the Prompt 68 autocomplete search - the caller always supplies
    // a real pattern (never literally null), so there's no separate "match everything"
    // branch to worry about here.
    @Query("""
            SELECT c FROM CaretakerProfile c
            JOIN User u ON u.id = c.userId
            WHERE LOWER(u.fullName) LIKE :pattern
            ORDER BY u.fullName ASC
            """)
    List<CaretakerProfile> searchByFullNamePattern(@Param("pattern") String pattern, Pageable pageable);
}
