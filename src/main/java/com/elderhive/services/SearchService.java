package com.elderhive.services;

import com.elderhive.dtos.Search.CaretakerSearchResult;
import com.elderhive.dtos.Search.ElderProfileSearchResult;
import com.elderhive.entities.User;

import java.util.List;

public interface SearchService {

    /**
     * query, latitude, and longitude are all optional - a blank query with coordinates
     * supplied ("caretakers near me") is a real, deliberately-supported use case, not just
     * name search. distanceKm on each result is only ever populated when the caller
     * supplied coordinates AND that caretaker has their own lat/long set (Prompt 64).
     */
    List<CaretakerSearchResult> searchCaretakers(String query, Double latitude, Double longitude, Integer limit);

    /** Scoped to elder profiles the caller can access - never a global search. */
    List<ElderProfileSearchResult> searchElderProfiles(String query, Integer limit, User caller);
}
