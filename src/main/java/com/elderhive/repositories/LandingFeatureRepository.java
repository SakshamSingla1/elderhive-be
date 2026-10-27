package com.elderhive.repositories;

import com.elderhive.entities.LandingFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LandingFeatureRepository extends JpaRepository<LandingFeature, Long> {

    List<LandingFeature> findAllByOrderBySortOrderAsc();

    List<LandingFeature> findByIsActiveTrueOrderBySortOrderAsc();
}
