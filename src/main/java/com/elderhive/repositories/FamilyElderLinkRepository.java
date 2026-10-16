package com.elderhive.repositories;

import com.elderhive.entities.FamilyElderLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyElderLinkRepository extends JpaRepository<FamilyElderLink, Long> {

    List<FamilyElderLink> findByElderProfileId(Long elderProfileId);

    List<FamilyElderLink> findByFamilyUserId(Long familyUserId);

    Optional<FamilyElderLink> findByElderProfileIdAndFamilyUserId(Long elderProfileId, Long familyUserId);

    boolean existsByElderProfileIdAndFamilyUserId(Long elderProfileId, Long familyUserId);

    void deleteByElderProfileIdAndFamilyUserId(Long elderProfileId, Long familyUserId);
}
