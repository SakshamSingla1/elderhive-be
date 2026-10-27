package com.elderhive.repositories;

import com.elderhive.entities.LandingTestimonial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LandingTestimonialRepository extends JpaRepository<LandingTestimonial, Long> {

    List<LandingTestimonial> findAllByOrderBySortOrderAsc();

    List<LandingTestimonial> findByIsActiveTrueOrderBySortOrderAsc();
}
