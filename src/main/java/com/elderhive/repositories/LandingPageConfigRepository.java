package com.elderhive.repositories;

import com.elderhive.entities.LandingPageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LandingPageConfigRepository extends JpaRepository<LandingPageConfig, Long> {

    // Singleton-row table - the oldest (first-ever-created) row is "the" config.
    Optional<LandingPageConfig> findFirstByOrderByIdAsc();
}
