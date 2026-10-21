package com.elderhive.controllers;

import com.elderhive.dtos.CaretakerProfile.CaretakerProfileResponse;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.payload.ApiResponse;
import com.elderhive.payload.ResponseModel;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.FavoriteCaretakerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * No userType gate (matches ElderSphere's own controller - isAuthenticated() only). "For the
 * authenticated family member" in this prompt's own text describes the primary use case, not
 * a hard role restriction this project otherwise has no reason to enforce here.
 */
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteCaretakerController {

    private final FavoriteCaretakerService favoriteCaretakerService;
    private final UserRepository userRepository;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{caretakerId}")
    public ResponseEntity<ResponseModel<CaretakerProfileResponse>> addFavorite(@PathVariable Long caretakerId) throws GenericException {
        User caller = currentUser();
        return ApiResponse.createSuccess(favoriteCaretakerService.addFavorite(caller.getId(), caretakerId),
                "Caretaker added to favorites");
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{caretakerId}")
    public ResponseEntity<ResponseModel<String>> removeFavorite(@PathVariable Long caretakerId) throws GenericException {
        User caller = currentUser();
        favoriteCaretakerService.removeFavorite(caller.getId(), caretakerId);
        return ApiResponse.successResponse("Removed", "Caretaker removed from favorites");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ResponseModel<List<CaretakerProfileResponse>>> listFavorites() throws GenericException {
        User caller = currentUser();
        return ApiResponse.successResponse(favoriteCaretakerService.listFavorites(caller.getId()), "Favorite caretakers fetched successfully");
    }

    private User currentUser() throws GenericException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found."));
    }
}
