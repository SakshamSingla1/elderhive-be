package com.elderhive.repositories;

import com.elderhive.entities.FavoriteCaretaker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteCaretakerRepository extends JpaRepository<FavoriteCaretaker, Long> {

    boolean existsByFamilyUserIdAndCaretakerId(Long familyUserId, Long caretakerId);

    Optional<FavoriteCaretaker> findByFamilyUserIdAndCaretakerId(Long familyUserId, Long caretakerId);

    List<FavoriteCaretaker> findByFamilyUserIdOrderByCreatedAtDesc(Long familyUserId);

    void deleteByFamilyUserIdAndCaretakerId(Long familyUserId, Long caretakerId);
}
