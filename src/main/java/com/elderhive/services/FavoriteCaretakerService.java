package com.elderhive.services;

import com.elderhive.dtos.CaretakerProfile.CaretakerProfileResponse;
import com.elderhive.exceptions.GenericException;

import java.util.List;

public interface FavoriteCaretakerService {

    CaretakerProfileResponse addFavorite(Long familyUserId, Long caretakerId) throws GenericException;

    void removeFavorite(Long familyUserId, Long caretakerId) throws GenericException;

    List<CaretakerProfileResponse> listFavorites(Long familyUserId);
}
