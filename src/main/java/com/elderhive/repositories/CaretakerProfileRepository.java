package com.elderhive.repositories;

import com.elderhive.entities.CaretakerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CaretakerProfileRepository extends JpaRepository<CaretakerProfile, Long> {

    Optional<CaretakerProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
