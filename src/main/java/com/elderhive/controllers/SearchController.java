package com.elderhive.controllers;

import com.elderhive.dtos.Search.CaretakerSearchResult;
import com.elderhive.dtos.Search.ElderProfileSearchResult;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Both authenticated (isAuthenticated() only, no further role gate) - unlike ElderSphere's
 * own public /search/caretakers, ElderHive's SecurityConfig deliberately keeps everything
 * authenticated by default, and CaretakerProfileController's own browsing endpoints
 * (Prompt 64) already follow that same narrower posture.
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/caretakers")
    public ResponseEntity<ResponseModel<List<CaretakerSearchResult>>> searchCaretakers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.successResponse(searchService.searchCaretakers(query, latitude, longitude, limit),
                "Caretakers fetched successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/elder-profiles")
    public ResponseEntity<ResponseModel<List<ElderProfileSearchResult>>> searchElderProfiles(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer limit) throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(searchService.searchElderProfiles(query, limit, caller),
                "Elder profiles fetched successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
