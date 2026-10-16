package com.elderhive.repositories;

import com.elderhive.entities.ElderProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ElderProfileRepository extends JpaRepository<ElderProfile, Long> {

    List<ElderProfile> findByFamilyUserId(Long familyUserId);

    Page<ElderProfile> findByFamilyUserId(Long familyUserId, Pageable pageable);

    Optional<ElderProfile> findByElderUserId(Long elderUserId);
}
