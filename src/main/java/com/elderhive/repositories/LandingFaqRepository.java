package com.elderhive.repositories;

import com.elderhive.entities.LandingFaq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LandingFaqRepository extends JpaRepository<LandingFaq, Long> {

    List<LandingFaq> findAllByOrderBySortOrderAsc();

    List<LandingFaq> findByIsActiveTrueOrderBySortOrderAsc();
}
