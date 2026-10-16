package com.elderhive.repositories;

import com.elderhive.entities.ElderProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ElderProfileRepository extends JpaRepository<ElderProfile, Long> {

    List<ElderProfile> findByFamilyUserId(Long familyUserId);

    Page<ElderProfile> findByFamilyUserId(Long familyUserId, Pageable pageable);

    Optional<ElderProfile> findByElderUserId(Long elderUserId);

    // Profiles a family member either owns (family_user_id) or co-manages via an accepted
    // FamilyElderLink (Prompt 62) — a plain ON-clause join against FamilyElderLink's own
    // scalar elderProfileId field (no @ManyToOne mapping between these entities exists or
    // is needed). Backs the "list my elders" controller endpoint, which otherwise wouldn't
    // let a co-managing family member find a profile they don't personally own.
    @Query("""
            SELECT DISTINCT ep FROM ElderProfile ep
            LEFT JOIN FamilyElderLink link ON link.elderProfileId = ep.id
            WHERE ep.familyUserId = :familyUserId OR link.familyUserId = :familyUserId
            """)
    Page<ElderProfile> findOwnedOrCoManagedByFamilyUserId(@Param("familyUserId") Long familyUserId, Pageable pageable);

    @Query("""
            SELECT DISTINCT ep FROM ElderProfile ep
            LEFT JOIN FamilyElderLink link ON link.elderProfileId = ep.id
            WHERE ep.familyUserId = :familyUserId OR link.familyUserId = :familyUserId
            """)
    List<ElderProfile> findOwnedOrCoManagedByFamilyUserId(@Param("familyUserId") Long familyUserId);
}
