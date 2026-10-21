package com.elderhive.services.impl;

import com.elderhive.dtos.CaretakerProfile.CaretakerProfileResponse;
import com.elderhive.entities.FavoriteCaretaker;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.FavoriteCaretakerRepository;
import com.elderhive.services.CaretakerProfileService;
import com.elderhive.services.FavoriteCaretakerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Depends on CaretakerProfileService (not its repository directly) for both the
 * existence-check and the response mapping - unlike FamilyElderLinkServiceImpl's own
 * precedent of depending on a sibling repository, CaretakerProfileService#getById has no
 * caller/access-check parameter to work around, so reusing it here avoids duplicating its
 * User-enrichment mapping logic for no reason. Returns CaretakerProfileResponse (Prompt 64),
 * not ElderSphere's richer CaretakerSearchResultDTO - that DTO belongs to Phase 9's search
 * endpoint (Prompt 68), which doesn't exist yet.
 */
@Service
@RequiredArgsConstructor
public class FavoriteCaretakerServiceImpl implements FavoriteCaretakerService {

    private final FavoriteCaretakerRepository favoriteCaretakerRepository;
    private final CaretakerProfileService caretakerProfileService;

    @Override
    @Transactional
    public CaretakerProfileResponse addFavorite(Long familyUserId, Long caretakerId) throws GenericException {
        CaretakerProfileResponse caretaker = caretakerProfileService.getById(caretakerId);

        if (favoriteCaretakerRepository.existsByFamilyUserIdAndCaretakerId(familyUserId, caretakerId)) {
            throw new GenericException(ExceptionCodeEnum.CONFLICT, "Caretaker is already in your favorites");
        }

        favoriteCaretakerRepository.save(FavoriteCaretaker.builder()
                .familyUserId(familyUserId)
                .caretakerId(caretakerId)
                .build());
        return caretaker;
    }

    @Override
    @Transactional
    public void removeFavorite(Long familyUserId, Long caretakerId) throws GenericException {
        favoriteCaretakerRepository.findByFamilyUserIdAndCaretakerId(familyUserId, caretakerId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.DATA_NOT_FOUND, "This caretaker is not in your favorites"));
        favoriteCaretakerRepository.deleteByFamilyUserIdAndCaretakerId(familyUserId, caretakerId);
    }

    @Override
    public List<CaretakerProfileResponse> listFavorites(Long familyUserId) {
        List<CaretakerProfileResponse> favorites = new ArrayList<>();
        for (FavoriteCaretaker favorite : favoriteCaretakerRepository.findByFamilyUserIdOrderByCreatedAtDesc(familyUserId)) {
            try {
                favorites.add(caretakerProfileService.getById(favorite.getCaretakerId()));
            } catch (GenericException e) {
                // A FK CASCADE means this can't actually happen (deleting a CaretakerProfile
                // deletes its favorite rows too) - defensive only, mirrors ElderSphere's own
                // null-filtering here rather than letting one bad row 500 the whole list.
            }
        }
        return favorites;
    }
}
